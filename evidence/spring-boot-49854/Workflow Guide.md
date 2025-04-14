# Additional Note

This workflow is designed to instrument and analyze the Spring Boot source code to locate the root cause of the issue reported in:

**https://github.com/spring-projects/spring-boot/issues/49854**

The steps outlined below capture the exact process used to reproduce the bug, collect runtime context, and prepare the data for AI-assisted bug localization.

---

# 1. Compile the Instrumentation Tool

```powershell
cd \path\to\scenario-based-runtime-context-for-ai; $env:JAVA_HOME="/path/to/jdk17" ; $env:Path="$env:JAVA_HOME\bin;$env:Path" ; mvn -f .\poc\pom.xml clean install -DskipTests
```

# 2. Instrument the Code

### 2.1 Configure the Target Directories for Instrumentation
Add the following paths to `target-folders.txt`:
```text
/path/to/spring-boot/module
/path/to/spring-boot/core
```

### 2.2 Execute Instrumentation
```powershell
.\run-instrumentation-demo.ps1 -TargetFoldersFile ".\target-folders.txt" -SkipBuildAndTest
```
After successful execution, the source code in the directories configured in `target-folders.txt` will be instrumented, and the following files will be generated in the current directory:
* `comment-mapping.txt`
* `event_dictionary.txt`

# 3. Compile the Instrumented Code

### 3.1 Modify `build.gradle` in the Root Directory
Apply the following changes:
```groovy
subprojects {
	apply plugin: "org.springframework.boot.conventions"

	repositories {
		mavenCentral()
+       maven {
+           url = uri("file:////path/to/your/maven/repository")
+       }		
		maven {
			name = "Shibboleth Releases"
			url = "https://build.shibboleth.net/nexus/content/repositories/releases"
			content {
				includeGroup "org.opensaml"
				includeGroup "net.shibboleth"
			}
		}
		spring.mavenRepositories()
	}

+	plugins.withType(JavaPlugin) {
+		dependencies {
+			implementation "com.example:instrumentor-log-monitor:1.0-SNAPSHOT"
+		}
+	}	

	configurations.all {
		resolutionStrategy.cacheChangingModulesFor 0, "minutes"
	}
}

+// skipAllTests
+if (System.properties.containsKey('skipAllTests')) {
+	allprojects {
+		afterEvaluate {
+			tasks.configureEach { task ->
+				if (task instanceof Test) {
+					task.enabled = false
+				}
+				String nameLower = task.name.toLowerCase()
+				if (nameLower.contains('test') || nameLower.contains('check') || nameLower.contains('javadoc')) {
+					task.enabled = false
+				}
+			}
+		}
+	}
+}
```

### 3.2 Modify `build.gradle` in the `buildSrc` Directory
Apply the following changes:
```groovy
repositories {
	mavenCentral()
+   maven {
+       url = uri("file:////path/to/your/maven/repository")
+   }			
	spring.mavenRepositoriesFor("${springFrameworkVersion}")
	gradlePluginPortal()
	maven { url = "https://repo.spring.io/snapshot" }
}

+// skipAllTests: Controlled by system property -D to ensure it takes effect for buildSrc as well
+if (System.properties.containsKey('skipAllTests')) {
+	tasks.configureEach { t ->
+		String nameLower = t.name.toLowerCase()
+		if (nameLower.contains('test') || nameLower.contains('check') || nameLower.contains('checkstyle')) {
+			t.enabled = false
+		}
+	}
+	// Originally jar.dependsOn check triggers check -> test, remove this dependency when skipping
+	jar.setDependsOn(jar.dependsOn.findAll { it.toString() != 'check' })
+} else {
	jar.dependsOn check
+}
```

### 3.3 Compile
Switch to the Java version required by the specific Spring Boot version (e.g., Spring Boot 4.0.5 requires Java 25), then run:
```bash
cd /path/to/spring-boot
./gradlew clean publishToMavenLocal --no-build-cache --no-daemon  --refresh-dependencies -DskipAllTests -x :cli:spring-boot-cli:fullJar -x :cli:spring-boot-cli:publishToMavenLocal -x :documentation:spring-boot-docs:publishToMavenLocal 
```

# 4. Test the Upstream Project

Taking issue `#49854` as an example:
```powershell
$env:JAVA_HOME="/path/to/jdk21" ; $env:Path="$env:JAVA_HOME\bin;$env:Path"; cd /path/to/spring-boot-pathpatternrequestmatcher;  mvn clean test
```
*Note:* Keep only one test class that can reproduce the bug (e.g., keep only `MockWithSecurityFilterChainTest.java` and delete the rest) to avoid log explosion.

After the tests finish, the instrumentation logs will be automatically saved. For example:
* `/path/to/spring-boot-pathpatternrequestmatcher/instrumentor-events-20260411_203220-junit-listener.txt`
* `/path/to/spring-boot-pathpatternrequestmatcher/instrumentor-log-20260411_203220-junit-listener.txt`

# 5. Restore Spring Boot Source to Clean State 

```powershell
cd /path/to/spring-boot ; git restore . ; git clean -fd .
```

# 6. Analyze Logs to Extract Denoised Data

```powershell
cd \path\to\scenario-based-runtime-context-for-ai; $env:JAVA_HOME="/path/to/jdk17" ; $env:Path="$env:JAVA_HOME\bin;$env:Path" 

.\process-logs-demo.ps1 `
    -TargetFoldersFile ".\target-folders.txt" `
    -LogFile "/path/to/spring-boot-pathpatternrequestmatcher/instrumentor-log-20260411_203220-junit-listener.txt" `
    -CommentMappingFile ".\comment-mapping.txt" `
    -EventsFile "/path/to/spring-boot-pathpatternrequestmatcher/instrumentor-events-20260411_203220-junit-listener.txt"
```
Upon successful execution, the following files will be generated in the current directory:
* `final-output-calltree.json`
* `final-output-calltree.md`
* `final-output-combined.json`
* `final-output-combined.md`
* `final-output-happensbefore.json`
* `final-output-happensbefore.md`

# 7. Generate the AI Prompt

```powershell
cd \path\to\scenario-based-runtime-context-for-ai\poc\denoised-data-ai-app; python generate_bug_localization_prompt.py
```
Follow the interactive prompts to input the relevant information. Finally, the following file will be generated in the current directory:
* `AI_Bug_Localization_Prompt.md`

# 8. Submit the Prompt to AI for Analysis and Get Results
Feed the generated prompt to the AI to analyze and retrieve the bug localization results.
