# Workflow Guide — Spring Boot #50021

> **📖 Reading the results only?** You don't need this guide. Go back to the [Case README](README.md) for navigation.
>
> **🛠️ Reproducing the full pipeline?** Continue below.

---

## Prerequisites

| Requirement | Purpose |
|-------------|---------|
| JDK 17 | Compile the instrumentation tool |
| JDK 21 | Compile Spring Boot 3.x and run the reproducer |
| Maven | Build the instrumentation tool and the reproducer |
| Gradle | Build Spring Boot from source |
| Spring Boot source (matching the issue version) | Instrumentation target |
| ~30–60 min | Mostly compilation time |

If you get stuck at any step, the pre-generated artifacts under this directory serve as the expected output for reference:

| Step | Expected output | Reference location |
|------|-----------------|--------------------|
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

## Step 2. Instrument the Code

### 2.1 Configure the Target Directories
Add the following paths to `target-folders.txt`:
```text
/path/to/spring-boot/spring-boot-project
```

### 2.2 Execute Instrumentation
```powershell
.\run-instrumentation-demo.ps1 -TargetFoldersFile ".\target-folders.txt" -SkipBuildAndTest
```

**Output** (generated in working directory):
* `comment-mapping.txt`
* `event_dictionary.txt`

## Step 3. Compile the Instrumented Code

### 3.1 Modify `build.gradle` in the Root Directory
Apply the following changes:
```groovy
subprojects {
	apply plugin: "org.springframework.boot.conventions"

	repositories {
		mavenCentral()
+       maven {
+           url = uri("file:///D:/maven/repository")
+       }	
		spring.mavenRepositories()
	}

+	plugins.withType(JavaPlugin) {
+		dependencies {
+			implementation "com.example:instrumentor-log-monitor:1.0-SNAPSHOT"
+		}
	}		

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
+       url = uri("file:///D:/maven/repository")
+   }		
	spring.mavenRepositoriesFor("${springFrameworkVersion}")
	gradlePluginPortal()
}

+// skipAllTests: Controlled by system property -D to ensure it takes effect for buildSrc as well
+if (System.properties.containsKey('skipAllTests')) {
+	tasks.configureEach { t ->
+		String nameLower = t.name.toLowerCase()
+		if (nameLower.contains('test') || nameLower.contains('check') || nameLower.contains('checkstyle')) {
+			t.enabled = false
+		}
+	}
+	jar.setDependsOn(jar.dependsOn.findAll { it.toString() != 'check' })
+} else {
	jar.dependsOn check
+}
```

### 3.3 Compile
```powershell
$env:JAVA_HOME="/path/to/jdk21" ; $env:Path="$env:JAVA_HOME/bin;$env:Path"
cd /path/to/spring-boot
./gradlew clean publishToMavenLocal `
    -x :spring-boot-project:spring-boot-docs:publishToMavenLocal `
    -x :spring-boot-project:spring-boot-tools:spring-boot-cli:publishToMavenLocal `
    --no-build-cache -DskipAllTests
```

## Step 4. Run the Reproducer

The reproducer source is in the [`reproducer/`](reproducer/) directory.

```powershell
$env:JAVA_HOME="/path/to/jdk21" ; $env:Path="$env:JAVA_HOME/bin;$env:Path"
cd /path/to/spring-boot-50021/reproducer
mvn clean compile dependency:copy-dependencies -DoutputDirectory=target/lib
java -cp "target\classes;target\lib\*" com.example.ReproducerApplication
```

> **⚠️ Tip:** Keep only the minimal code needed to reproduce the bug to avoid log explosion.

**Output** (generated in reproducer directory):
* `instrumentor-events-yyyymmdd_hhmmss-shutdown.txt`
* `instrumentor-log-yyyymmdd_hhmmss-shutdown.txt`

## Step 5. Restore Spring Boot Source to Clean State

```powershell
cd /path/to/spring-boot ; git restore . ; git clean -fd .
```

## Step 6. Analyze Logs to Extract Denoised Data

```powershell
cd \path\to\scenario-based-runtime-context-for-ai
$env:JAVA_HOME="/path/to/jdk17" ; $env:Path="$env:JAVA_HOME\bin;$env:Path" 

.\process-logs-demo.ps1 `
    -TargetFoldersFile ".\target-folders.txt" `
    -LogFile "/path/to/reproducer/instrumentor-log-yyyymmdd_hhmmss-shutdown.txt" `
    -CommentMappingFile ".\comment-mapping.txt" `
    -EventsFile "/path/to/reproducer/instrumentor-events-yyyymmdd_hhmmss-shutdown.txt"
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

## Step 7. Generate the AI Prompt

```powershell
cd \path\to\scenario-based-runtime-context-for-ai\poc\denoised-data-ai-app
python generate_bug_localization_prompt.py
```

Follow the interactive prompts to input the relevant information.

**Output** — corresponds to [`2_ai_prompt/AI_Bug_Localization_Prompt.md`](2_ai_prompt/AI_Bug_Localization_Prompt.md)

## Step 8. Submit the Prompt to AI for Analysis

Feed the generated prompt to the LLM.

**Output** — corresponds to [`3_ai_response/Bug_Localization_and_Fix_Plan.md`](3_ai_response/Bug_Localization_and_Fix_Plan.md)

---

## Official Fix Reference

The core fix identified through this workflow aligns exactly with the official Spring Boot commit:

**Commit:** [`edcc937`](https://github.com/spring-projects/spring-boot/commit/edcc937adde0f168e3e9781ba19c6e923864f9b9)

---

> **← Back to [Case README](README.md)**