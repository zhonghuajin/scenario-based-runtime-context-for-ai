# ZooKeeper Scenario Testing Guide

> **⚠️ Disclaimer:** 
> This document is solely intended to demonstrate that the demo can be applied to ZooKeeper for testing. It does not guarantee complete success if you follow this guide. The author did not use the exact scripts provided here when testing ZooKeeper. Due to significant environmental differences, this guide and the accompanying scripts are for reference only.

This document explains how to use the `runtime-context-for-ai` project to instrument Apache ZooKeeper, collect zero-noise runtime context data, and generate highly focused prompts for Large Language Models (LLMs).

## Prerequisites

Before starting, ensure you have cloned both this repository and the ZooKeeper source code. Set up the following environment variables in your terminal (adjust the paths according to your actual workspace):

### Bash (Linux / macOS)
```bash
# The root directory of this project (runtime-context-for-ai)
export CTX_HOME="/path/to/runtime-context-for-ai"
# The root directory of the ZooKeeper source code
export ZK_HOME="/path/to/zookeeper"
# The target directory for instrumentation (ZooKeeper Server)
export TARGET_DIR="$ZK_HOME/zookeeper-server/src/main/java/org/apache/zookeeper/server"
```

### PowerShell (Windows)
```powershell
# The root directory of this project (runtime-context-for-ai)
$env:CTX_HOME="C:\path\to\runtime-context-for-ai"
# The root directory of the ZooKeeper source code
$env:ZK_HOME="C:\path\to\zookeeper"
# The target directory for instrumentation (ZooKeeper Server)
$env:TARGET_DIR="$env:ZK_HOME\zookeeper-server\src\main\java\org\apache\zookeeper\server"
```

## 1. Code Instrumentation

Execute the following commands in sequence to instrument the core ZooKeeper Server code, map the encodings, and activate the logging calls:

```bash
# 1. Main Instrumentation
java -jar $CTX_HOME/demos/instrumentor/target/instrumentor-1.0-SNAPSHOT.jar "$TARGET_DIR"

# 2. Encoding Mapping (Generates ID mappings to reduce performance overhead)
java -jar $CTX_HOME/demos/instrumentor-with-encoding/target/instrumentor-with-encoding-1.0-SNAPSHOT.jar map "$TARGET_DIR/"

# 3. Activator (Replaces comments with actual logging function calls)
# Ensure you are using JDK 17+
java -jar $CTX_HOME/demos/instrumentor-activator/target/instrumentor-activator-1.0-SNAPSHOT.jar activate "$TARGET_DIR/"
```

## 2. Compilation

Before compiling ZooKeeper, you need to inject the log monitor dependency from this project.

1. Navigate to the ZooKeeper source directory:
   ```bash
   cd $ZK_HOME
   ```

2. Edit `$ZK_HOME/zookeeper-server/pom.xml` and add the following dependency inside the `<dependencies>` block:
   ```xml
   <dependency>
     <groupId>com.example</groupId>
     <artifactId>instrumentor-log-monitor</artifactId>
     <version>1.0-SNAPSHOT</version>
     <scope>compile</scope>
   </dependency> 
   ```

3. Build and package ZooKeeper (skip tests to speed up the process):
   ```bash
   mvn clean install -DskipTests
   ```

## 3. Execution & Triggering Scenarios

1. **Prepare the Configuration File**:
   Copy `conf/zoo_sample.cfg` from the ZooKeeper root directory and rename it to `zoo.cfg`.
   Modify the `dataDir` property in `zoo.cfg`:
   ```properties
   dataDir=/tmp/zookeeper/data
   ```

2. **Start the ZooKeeper Server**:
   Use the provided script in this directory to extract and start the instrumented ZooKeeper server:
   ```bash
   chmod +x start-zkserver.sh
   ./start-zkserver.sh
   ```
   *(For Windows, run `.\start-zkserver.ps1`)*

3. **Start the ZooKeeper Client**:
   Use the provided script to connect to the local server:
   ```bash
   chmod +x start-zkcli.sh
   ./start-zkcli.sh
   ```
   *(For Windows, run `.\start-zkcli.ps1`)*
   
   Once connected, execute some commands in the client (e.g., `create /test node`, `get /test`) to trigger specific execution paths in the server.

## 4. Collect Zero-Noise Data

The instrumented code runs a background HTTP monitor service (default port `19898`). You can use the following API endpoints to manage and collect the runtime logs for your specific scenario:

```bash
# 1. Check the status and count of currently collected logs
curl http://localhost:19898/status

# 2. Flush the in-memory logs to the default file (instrumentor-log-{timestamp}.txt)
curl http://localhost:19898/flush

# 3. Clear the in-memory logs (to prepare for recording the next isolated scenario)
curl http://localhost:19898/clear

# 4. Check the status again (it should now be empty)
curl http://localhost:19898/status
```

## 5. Generate LLM Prompts

Once you have collected the log files, you can use the `process-logs-demo.ps1` script in the project root (or manually run the Java processing chain: `log-deduplicator` -> `block-pruner` -> `data-structuring`) to process these logs.

After processing, the system will generate structured files such as `final-output-combined.md`. These files have all unexecuted code branches pruned (zero-noise) and retain the exact execution paths and `happens-before` relationships. You can directly feed this high-quality context to an LLM for code review, bug diagnosis, or refactoring suggestions.
