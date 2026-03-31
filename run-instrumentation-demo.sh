#!/bin/bash

# Color Definitions
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Parameter Parsing (Uses the target folder if provided, otherwise uses default)
TARGET_FOLDER="${1:-./demos/instrumentor-test/src/main/java/com/example/instrumentor/happens}"

# Verify if the path exists
if [ ! -d "$TARGET_FOLDER" ]; then
    echo -e "${RED}Error: Target folder does not exist: $TARGET_FOLDER${NC}"
    exit 1
fi

echo -e "${YELLOW}Target folder: $TARGET_FOLDER${NC}"

# 1. Restore Source Code (Undo instrumentation changes)
echo -e "${CYAN}Restoring the instrumented source folder using Git...${NC}"
git restore "$TARGET_FOLDER"
git clean -fd "$TARGET_FOLDER"

# 2. Check Java Environment Variables
echo -e "${CYAN}Checking Java environment variables...${NC}"
if [ -z "$JAVA_HOME" ]; then
    echo -e "${RED}Error: JAVA_HOME environment variable not configured. Please set JAVA_HOME to point to your JDK installation directory.${NC}"
    exit 1
fi
echo -e "${GREEN}Using JAVA_HOME: $JAVA_HOME${NC}"
export PATH="$JAVA_HOME/bin:$PATH"

# 3. First Build (Build the instrumentor itself)
echo -e "${CYAN}Executing mvn clean package to build the instrumentor...${NC}"
mvn -f ./demos/pom.xml clean package -DskipTests
if [ $? -ne 0 ]; then
    echo -e "${RED}Maven build failed${NC}"
    exit 1
fi

# 4. Execute Instrumentation Java Commands
echo -e "${CYAN}Executing code instrumentation (Instrumentor)...${NC}"

# 4.1 Main Instrumentation
java -jar ./demos/instrumentor/target/instrumentor-1.0-SNAPSHOT.jar "$TARGET_FOLDER"
if [ $? -ne 0 ]; then
    echo -e "${YELLOW}Main instrumentation step returned non-zero exit code: $?${NC}"
fi

# 4.2 Encoding Mapping
java -jar ./demos/instrumentor-with-encoding/target/instrumentor-with-encoding-1.0-SNAPSHOT.jar map "$TARGET_FOLDER/"
if [ $? -ne 0 ]; then
    echo -e "${YELLOW}Encoding mapping step returned non-zero exit code: $?${NC}"
fi

# 4.3 Activator
java -jar ./demos/instrumentor-activator/target/instrumentor-activator-1.0-SNAPSHOT.jar activate "$TARGET_FOLDER/"
if [ $? -ne 0 ]; then
    echo -e "${YELLOW}Activator step returned non-zero exit code: $?${NC}"
fi

# 5. Second Build (Including the instrumented code)
echo -e "${CYAN}Executing mvn clean package again...${NC}"
mvn -f ./demos/pom.xml clean package -DskipTests
if [ $? -ne 0 ]; then
    echo -e "${RED}Second Maven build failed${NC}"
    exit 1
fi

# 6. Execute Testing
echo -e "${CYAN}Executing SyncTest...${NC}"
java -cp ./demos/instrumentor-test/target/instrumentor-test-1.0-SNAPSHOT.jar com.example.instrumentor.happens.before.SyncTest
if [ $? -ne 0 ]; then
    echo -e "${YELLOW}Test execution returned non-zero exit code: $?${NC}"
fi

echo -e "${GREEN}Instrumentation and testing phase completed. Please check the generated log file timestamp and use process-logs-demo.sh for subsequent processing.${NC}"