# Bug Localization and Fix Plan

## 1. Fact Tracing Path

**Working backward from the symptom:**

1. In **Thread-1**, `SpringApplication.run()` executes up to `refreshContext(context)` and then enters the exception handling path `handleRunFailure()`, indicating that an exception occurred during the context refresh phase.

2. In the exception handling path, `FailureAnalyzers.reportException()` → `NoSuchBeanDefinitionFailureAnalyzer` is constructed and analyzes the exception, confirming that the failure is due to a missing Bean definition.

3. Tracing back to the condition evaluation phase, when `OnBeanCondition.evaluateConditionalOnBean()` → `getMatchingBeans()` is executed, the search result for the Bean of type `DispatcherServletPath` is empty (`typeMatchedNames.isEmpty()` is true). It calls `result.recordUnmatchedType(type)` and ultimately returns `ConditionOutcome.noMatch`.

4. This means that the `@ConditionalOnBean(DispatcherServletPath.class)` condition on `ServletWebSecurityAutoConfiguration` fails, causing its internally defined `PathPatternRequestMatcher.Builder` Bean to not be registered.

5. Tracing back to the auto-configuration sorting phase, `AutoConfigurationSorter.getInPriorityOrder()` first sorts alphabetically, then by `@AutoConfigureOrder`, and finally performs a topological sort based on `@AutoConfigureBefore/@AutoConfigureAfter`. In `sortByAnnotation()` → `getClassesRequestedAfter()`, there is no explicit sorting relationship declared between `MockMvcAutoConfiguration` and `ServletWebSecurityAutoConfiguration`.

6. Tracing back to the auto-configuration metadata loading, `AutoConfigurationMetadataLoader.loadMetadata()` loads `META-INF/spring-autoconfigure-metadata.properties`. When querying `MockMvcAutoConfiguration` via `getSet(className, "AutoConfigureAfter")` and `getSet(className, "AutoConfigureBefore")`, it finds no declaration that it needs to execute before `ServletWebSecurityAutoConfiguration`.

7. Therefore, in the `@WebMvcTest` slice test, the condition evaluation of `ServletWebSecurityAutoConfiguration` precedes the registration of the mock `DispatcherServletPath` Bean by `MockMvcAutoConfiguration`, leading to the condition matching failure.

## 2. Root Cause Analysis

- **File**: `org/springframework/boot/webmvc/test/autoconfigure/MockMvcAutoConfiguration.java` (and its accompanying auto-configuration metadata)
- **Function/Configuration**: The `@AutoConfiguration` declaration of the `MockMvcAutoConfiguration` class
- **Nature of the Defect**: `MockMvcAutoConfiguration` does not declare a sorting relationship with `ServletWebSecurityAutoConfiguration`. In the `@WebMvcTest` slice test, `MockMvcAutoConfiguration` is responsible for registering the mock `DispatcherServletPath` Bean, while the `@ConditionalOnBean(DispatcherServletPath.class)` condition in `ServletWebSecurityAutoConfiguration` depends on the existence of this Bean. Due to the lack of an `@AutoConfigureBefore(ServletWebSecurityAutoConfiguration.class)` declaration, `AutoConfigurationSorter.sortByAnnotation()` cannot guarantee that `MockMvcAutoConfiguration` is processed before `ServletWebSecurityAutoConfiguration` during topological sorting. This causes the mock Bean to be unregistered at the time of condition evaluation, resulting in a condition failure, and the `PathPatternRequestMatcher.Builder` Bean is not created.

## 3. Code Fix Implementation

We need to modify `MockMvcAutoConfiguration` to explicitly declare that it executes before `ServletWebSecurityAutoConfiguration`:

```java
// File: org/springframework/boot/webmvc/test/autoconfigure/MockMvcAutoConfiguration.java

package org.springframework.boot.webmvc.test.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.security.autoconfigure.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.DispatcherServlet;

// 🐛 [Bug Fix] Add before = ServletWebSecurityAutoConfiguration.class,
// ensuring that MockMvcAutoConfiguration is processed before ServletWebSecurityAutoConfiguration,
// so that the mock DispatcherServletPath bean is registered before the
// @ConditionalOnBean(DispatcherServletPath.class) condition evaluation of the security auto-configuration.
@AutoConfiguration(
    after = { DispatcherServletAutoConfiguration.class, WebMvcAutoConfiguration.class },
    before = { ServletWebSecurityAutoConfiguration.class }
)
@ConditionalOnWebApplication(type = Type.SERVLET)
public class MockMvcAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(org.springframework.boot.webmvc.autoconfigure.DispatcherServletPath.class)
    public org.springframework.boot.webmvc.autoconfigure.DispatcherServletPath dispatcherServletPath() {
        return () -> "";
    }

    // ... The rest of the MockMvc-related Bean definitions remain unchanged ...
}
```

At the same time, the corresponding auto-configuration metadata file needs to be updated:

```properties
# File: META-INF/spring-autoconfigure-metadata.properties
# (in the spring-boot-webmvc-test-autoconfigure module)

# 🐛 [Bug Fix] Add AutoConfigureBefore metadata declaration
org.springframework.boot.webmvc.test.autoconfigure.MockMvcAutoConfiguration.AutoConfigureBefore=org.springframework.boot.security.autoconfigure.servlet.ServletWebSecurityAutoConfiguration
```

## 4. Verification Logic

The reasons why this fix resolves the issue are as follows:

1. **Sorting Guarantee**: After adding `before = ServletWebSecurityAutoConfiguration.class`, `AutoConfigurationSorter.getClassesRequestedAfter("ServletWebSecurityAutoConfiguration")` will find that `MockMvcAutoConfiguration.getBefore()` contains `ServletWebSecurityAutoConfiguration` when iterating through all auto-configuration classes, thereby adding `MockMvcAutoConfiguration` to its "requested after" set. In the topological sort of `doSortByAfterAnnotation()`, `MockMvcAutoConfiguration` will definitely be ordered before `ServletWebSecurityAutoConfiguration`.

2. **Condition Evaluation Timing**: After sorting, the Bean definitions of `MockMvcAutoConfiguration` (including the mock `DispatcherServletPath`) are registered into the `BeanFactory` first. When the `@ConditionalOnBean(DispatcherServletPath.class)` condition of `ServletWebSecurityAutoConfiguration` is evaluated by `OnBeanCondition.getMatchingBeans()`, `getBeanDefinitionsForType(beanFactory, considerHierarchy, type, ...)` will be able to find the already registered mock Bean, `typeMatchedNames` will not be empty, and the condition matching will succeed.

3. **Correct Module Dependency Direction**: The test auto-configuration module (`spring-boot-webmvc-test-autoconfigure`) depends on the security auto-configuration module (`spring-boot-security-autoconfigure`), so referencing the `ServletWebSecurityAutoConfiguration` class within `MockMvcAutoConfiguration` is reasonable in terms of the module dependency direction and will not introduce circular dependencies.
