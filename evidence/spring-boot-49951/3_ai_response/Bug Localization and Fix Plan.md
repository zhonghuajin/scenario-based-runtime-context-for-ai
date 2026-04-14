# Bug Localization and Fix Plan

## 1. Factual Backtracking Path

1. **Symptom**: Test `failOnUnknownPropertiesShouldBeFalse` asserts `jsonMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)` is `false`, but it is `true`.

2. **`JsonMapper` creation** (Thread-3): In `JacksonAutoConfiguration.java`, the `jacksonJsonMapper` bean is created via `builder.build()` from a `JsonMapper.Builder` customized by `StandardJsonMapperBuilderCustomizer`.

3. **Builder customization** (Thread-3): The `customize(B builder)` method in `AbstractMapperBuilderCustomizer` is invoked. The trace shows `this.jacksonProperties.isUseJackson2Defaults()` returns `true` (because `spring.jackson.use-jackson2-defaults: true` is set in `application.yml`).

4. **`configureForJackson2()` call** (Thread-3): The executed branch is:
   ```java
   builder.configureForJackson2()
       .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS);
   ```
   `configureForJackson2()` restores **all** Jackson 2 defaults. In Jackson 2, `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES` was **enabled** by default (`true`). This call sets `FAIL_ON_UNKNOWN_PROPERTIES` back to `true`.

5. **No compensating disable**: After the `configureForJackson2()` block, `configureFeatures(builder, this.jacksonProperties.getDeserialization(), ...)` is called, but the trace shows `getDeserialization()` returns an empty map — the user hasn't explicitly configured any deserialization features. So `FAIL_ON_UNKNOWN_PROPERTIES` remains `true`.

6. **Root divergence**: In Jackson 3, the new default for `FAIL_ON_UNKNOWN_PROPERTIES` is `false`. Spring Boot 3 relied on this. But `configureForJackson2()` reverts it to the Jackson 2 default of `true`, and the customizer only disables `DateTimeFeature`s afterward — never `FAIL_ON_UNKNOWN_PROPERTIES`.

## 2. Root Cause Analysis
- **File**: `org/springframework/boot/jackson/autoconfigure/JacksonAutoConfiguration.java`
- **Function**: `AbstractMapperBuilderCustomizer.customize(B builder)`
- **The Flaw**: When `configureForJackson2()` is called to restore Jackson 2 defaults, it re-enables `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES` (which was `true` in Jackson 2). The code only disables `DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS` and `DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS` after this call, but does not disable `FAIL_ON_UNKNOWN_PROPERTIES`. This breaks Spring Boot 3's established behavior where unknown properties are silently ignored during deserialization.

## 3. Code Fix Implementation

**File**: `org/springframework/boot/jackson/autoconfigure/JacksonAutoConfiguration.java`  
**Method**: `AbstractMapperBuilderCustomizer.customize`

```java
protected void customize(B builder) {
    if (this.jacksonProperties.isUseJackson2Defaults()) {
        builder.configureForJackson2()
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS,
                     DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
            // 🐛 [Bug Fix] configureForJackson2() restores Jackson 2's default of
            // FAIL_ON_UNKNOWN_PROPERTIES=true.  Spring Boot 3 expects this to be
            // disabled for backward compatibility, so we must explicitly disable it.
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
    if (this.jacksonProperties.isFindAndAddModules()) {
        builder.findAndAddModules(getClass().getClassLoader());
    }
    Include propertyInclusion = this.jacksonProperties.getDefaultPropertyInclusion();
    if (propertyInclusion != null) {
        builder.serializationInclusion(propertyInclusion);
    }
    if (this.jacksonProperties.getTimeZone() != null) {
        builder.defaultTimeZone(this.jacksonProperties.getTimeZone());
    }
    configureVisibility(builder, this.jacksonProperties.getVisibility());
    configureFeatures(builder, this.jacksonProperties.getDeserialization(), builder::configure);
    configureFeatures(builder, this.jacksonProperties.getSerialization(), builder::configure);
    configureFeatures(builder, this.jacksonProperties.getMapper(), builder::configure);
    configureFeatures(builder, this.jacksonProperties.getDatatype().getDatetime(), builder::configure);
    configureFeatures(builder, this.jacksonProperties.getDatatype().getEnum(), builder::configure);
    configureFeatures(builder, this.jacksonProperties.getDatatype().getJsonNode(), builder::configure);
    configureDateFormat(builder);
    configurePropertyNamingStrategy(builder);
    configureModules(builder);
    configureLocale(builder);
    configureDefaultLeniency(builder);
    configureConstructorDetector(builder);
}
```

## 4. Verification Logic

The fix adds `.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)` immediately after `configureForJackson2()`. This ensures that:

1. **`failOnUnknownPropertiesShouldBeFalse`** passes: The mapper will have `FAIL_ON_UNKNOWN_PROPERTIES` disabled, matching Spring Boot 3 behavior.
2. **`deserializePageResponseWithUnknownFieldsShouldNotFail`** passes: Deserialization of JSON with unknown fields (e.g., `pageable`, `sort`, `first`, etc.) will succeed without throwing an exception.
3. **User override preserved**: If a user explicitly sets `spring.jackson.deserialization.fail-on-unknown-properties=true` in their configuration, the later `configureFeatures(builder, this.jacksonProperties.getDeserialization(), ...)` call will re-enable it, because user-specified features are applied **after** the default setup. The ordering in `customize()` guarantees this.
4. **No regression for non-jackson2-defaults users**: The fix is inside the `if (isUseJackson2Defaults())` block, so it only affects users who explicitly opt into Jackson 2 compatibility mode.