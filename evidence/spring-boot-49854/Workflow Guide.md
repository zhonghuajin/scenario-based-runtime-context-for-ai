# Workflow Guide — Spring Boot #49854

> **📖 Just reading the results?** You don't need this guide. Go back to the [Case README](README.md) for navigation.
>
> **🛠️ Reproducing the full pipeline?** Continue below.

---

## Overview

This workflow instruments and analyzes the Spring Boot source code to locate the root cause of:

**Issue:** [spring-projects/spring-boot#49854](https://github.com/spring-projects/spring-boot/issues/49854)

The steps below capture the exact process used to reproduce the bug, collect runtime context, and prepare the data for AI-assisted bug localization.

---

## Prerequisites

| Requirement | Purpose |
|-------------|---------|
| JDK 17 | Compile the instrumentation tool |
| JDK matching Spring Boot version (e.g., Java 25 for Spring Boot 4.0.5) | Compile Spring Boot from source and run the reproducer |
| Maven | Build the instrumentation tool and the reproducer |
| Gradle | Build Spring Boot from source |
| Spring Boot source (matching the issue version) | Instrumentation target |
| ~30–60 min | Mostly compilation time |

### Quick Reference: Step → Expected Output

If you get stuck at any step, the pre-generated artifacts in this directory serve as reference:

| Step | What it produces | Reference location |
|------|------------------|--------------------|
| Step 2 — Instrument | `comment-mapping.txt`, `event_dictionary.txt` | _(generated in working directory)_ |
| Step 4 — Collect logs | `instrumentor-log-*.txt`, `instrumentor-events-*.txt` | _(generated in reproducer directory)_ |
| Step 6 — Denoise | `calltree.md/json`, `combined.md/json`, `happensbefore.md/json` | [`1_runtime_evidence/`](1_runtime_evidence/) |
| Step 7 — Generate prompt | `AI_Bug_Localization_Prompt.md` | [`2_ai_prompt/`](2_ai_prompt/) |
| Step 8 — AI response | `Bug_Localization_and_Fix_Plan.md` | [`3_ai_response/`](3_ai_response/) |

---

## Step 1. Compile the Instrumentation Tool

```powershell
cd \path\to\scenario-based-runtime-context-for-ai
$env:JAVA_HOME="/path/to/jdk17" ; $env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -f .\poc\pom.xml clean install -DskipTests
```

---

## Step 2. Instrument the Code

### 2.1 Configure the Target Directories

Add the following paths to `target-folders.txt`:
```text
/path/to/spring-boot/module
/path/to/spring-boot/core
```

### 2.2 Execute Instrumentation

```powershell
.\run-instrumentation-demo.ps1 -TargetFoldersFile ".\target-folders.txt" -SkipBuildAndTest
```

After successful execution, the source code in the configured directories will be instrumented, and the following files will be generated in the current directory:
* `comment-mapping.txt`
* `event_dictionary.txt`

---

## Step 3. Compile the Instrumented Code

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
./gradlew clean publishToMavenLocal \
    --no-build-cache --no-daemon --refresh-dependencies \
    -DskipAllTests \
    -x :cli:spring-boot-cli:fullJar \
    -x :cli:spring-boot-cli:publishToMavenLocal \
    -x :documentation:spring-boot-docs:publishToMavenLocal
```

---

## Step 4. Run the Reproducer

The reproducer source is in the [`reproducer/`](reproducer/) directory.

```powershell
$env:JAVA_HOME="/path/to/jdk21" ; $env:Path="$env:JAVA_HOME\bin;$env:Path"
cd /path/to/spring-boot-49854/reproducer
mvn clean test
```

> **⚠️ Tip:** Keep only one test class that can reproduce the bug (e.g., keep only `MockWithSecurityFilterChainTest.java` and delete the rest) to avoid log explosion.

After the tests finish, the instrumentation logs will be automatically saved:
* `instrumentor-events-yyyymmdd_hhmmss-junit-listener.txt`
* `instrumentor-log-yyyymmdd_hhmmss-junit-listener.txt`

---

## Step 5. Restore Spring Boot Source to Clean State

```powershell
cd /path/to/spring-boot ; git restore . ; git clean -fd .
```

---

## Step 6. Analyze Logs to Extract Denoised Data

```powershell
cd \path\to\scenario-based-runtime-context-for-ai
$env:JAVA_HOME="/path/to/jdk17" ; $env:Path="$env:JAVA_HOME\bin;$env:Path"

.\process-logs-demo.ps1 `
    -TargetFoldersFile ".\target-folders.txt" `
    -LogFile "/path/to/reproducer/instrumentor-log-yyyymmdd_hhmmss-junit-listener.txt" `
    -CommentMappingFile ".\comment-mapping.txt" `
    -EventsFile "/path/to/reproducer/instrumentor-events-yyyymmdd_hhmmss-junit-listener.txt"
```

**Output** — these correspond to the artifacts in [`1_runtime_evidence/`](1_runtime_evidence/):

| Generated file | Corresponds to |
|----------------|----------------|
| `final-output-calltree.md` | [`1_runtime_evidence/calltree.md`](1_runtime_evidence/calltree.md) |
| `final-output-calltree.json` | [`1_runtime_evidence/calltree.json`](1_runtime_evidence/calltree.json) |
| `final-output-combined.md` | [`1_runtime_evidence/combined.md`](1_runtime_evidence/combined.md) |
| `final-output-combined.json` | [`1_runtime_evidence/combined.json`](1_runtime_evidence/combined.json) |
| `final-output-happensbefore.md` | [`1_runtime_evidence/happensbefore.md`](1_runtime_evidence/happensbefore.md) |
| `final-output-happensbefore.json` | [`1_runtime_evidence/happensbefore.json`](1_runtime_evidence/happensbefore.json) |

---

## Step 7. Generate the AI Prompt

```powershell
cd \path\to\scenario-based-runtime-context-for-ai\poc\denoised-data-ai-app
python generate_bug_localization_prompt.py
```

Follow the interactive prompts to input the relevant information.

**Output** — corresponds to [`2_ai_prompt/AI_Bug_Localization_Prompt.md`](2_ai_prompt/AI_Bug_Localization_Prompt.md)

---

## Step 8. Submit the Prompt to AI for Analysis

Feed the generated prompt to the LLM to analyze and retrieve the bug localization results.

**Output** — corresponds to [`3_ai_response/Bug_Localization_and_Fix_Plan.md`](3_ai_response/Bug_Localization_and_Fix_Plan.md)

---

> **← Back to [Case README](README.md)**