#!/bin/bash

# Color Definitions
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Parameter Parsing (Uses all provided folders, or the default if none provided)
if [ $# -gt 0 ]; then
    TARGET_FOLDERS=("$@")
else
    TARGET_FOLDERS=("./demos/instrumentor-test/src/main/java/com/example/instrumentor/happens")
fi

# Verify all paths exist
for folder in "${TARGET_FOLDERS[@]}"; do
    if [ ! -d "$folder" ]; then
        echo -e "${RED}Error: Target folder does not exist: $folder${NC}"
        exit 1
    fi
done

echo -e "${YELLOW}Target folders: ${TARGET_FOLDERS[*]}${NC}"

# 1. Restore Source Code (Undo instrumentation changes)
echo -e "${CYAN}Restoring the instrumented source folders using Git...${NC}"
for folder in "${TARGET_FOLDERS[@]}"; do
    git restore "$folder"
    git clean -fd "$folder"
done

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

# 4.1 Main Instrumentation (pass all folders as arguments)
java -jar ./demos/instrumentor/target/instrumentor-1.0-SNAPSHOT.jar "${TARGET_FOLDERS[@]}"
if [ $? -ne 0 ]; then
    echo -e "${YELLOW}Main instrumentation step returned non-zero exit code: $?${NC}"
fi

# 4.2 Encoding Mapping (pass all folders as arguments)
MAPPING_ARGS=("map")
for folder in "${TARGET_FOLDERS[@]}"; do
    MAPPING_ARGS+=("$folder/")
done
java -jar ./demos/instrumentor-with-encoding/target/instrumentor-with-encoding-1.0-SNAPSHOT.jar "${MAPPING_ARGS[@]}"
if [ $? -ne 0 ]; then
    echo -e "${YELLOW}Encoding mapping step returned non-zero exit code: $?${NC}"
fi

# 4.3 Activator (pass all folders as arguments)
ACTIVATOR_ARGS=("activate")
for folder in "${TARGET_FOLDERS[@]}"; do
    ACTIVATOR_ARGS+=("$folder/")
done
java -jar ./demos/instrumentor-activator/target/instrumentor-activator-1.0-SNAPSHOT.jar "${ACTIVATOR_ARGS[@]}"
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