#!/bin/bash

# Color Definitions
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Default Parameter Values
TARGET_FOLDER="./demos/instrumentor-test/src/main/java/com/example/instrumentor/happens"
PROJECT_FOLDER="demos/instrumentor-test/"
TIMESTAMP=""

# Parse named parameters using getopts
while getopts "t:f:p:" opt; do
  case $opt in
    t) TIMESTAMP="$OPTARG" ;;
    f) TARGET_FOLDER="$OPTARG" ;;
    p) PROJECT_FOLDER="$OPTARG" ;;
    \?) echo -e "${RED}Invalid option -$OPTARG${NC}" >&2; exit 1 ;;
  esac
done

# Validate required parameter t (Timestamp)
if [ -z "$TIMESTAMP" ]; then
    echo -e "${RED}Error: Please provide a timestamp using the -t flag, e.g., ./process-logs-demo.sh -t 20260329_171510${NC}"
    exit 1
fi

# Verify if target folder exists
if [ ! -d "$TARGET_FOLDER" ]; then
    echo -e "${RED}Error: Target folder does not exist: $TARGET_FOLDER${NC}"
    exit 1
fi

# Check Java environment variables
echo -e "${CYAN}Checking Java environment variables...${NC}"
if [ -z "$JAVA_HOME" ]; then
    echo -e "${RED}Error: JAVA_HOME environment variable not configured. Please set JAVA_HOME to point to your JDK installation directory.${NC}"
    exit 1
fi
echo -e "${GREEN}Using JAVA_HOME: $JAVA_HOME${NC}"
export PATH="$JAVA_HOME/bin:$PATH"

echo -e "${CYAN}Starting log processing and data structuring (Current timestamp: $TIMESTAMP)...${NC}"

# 1. Restore Source Code (Undo instrumentation changes)
echo -e "${CYAN}Restoring the instrumented source folder using Git...${NC}"
git restore "$TARGET_FOLDER"
git clean -fd "$TARGET_FOLDER"

# 2. Log Deduplication
echo -e "${YELLOW}Executing Log Deduplicator...${NC}"
java -jar ./demos/log-deduplicator/target/log-deduplicator-1.0-SNAPSHOT.jar "./instrumentor-log-${TIMESTAMP}.txt" "./instrumentor-log-${TIMESTAMP}_dedup.txt"

# 3. Block Pruning
echo -e "${YELLOW}Executing Block Pruner...${NC}"
java -jar ./demos/block-pruner/target/block-pruner-1.0-SNAPSHOT.jar "$PROJECT_FOLDER" ./comment-mapping.txt "./instrumentor-log-${TIMESTAMP}_dedup.txt" pruned/

# 4. Data Structuring
echo -e "${YELLOW}Executing Data Structuring...${NC}"
java -jar ./demos/data-structuring/target/data-structuring-1.0-SNAPSHOT.jar ./pruned/ ./comment-mapping.txt "./instrumentor-log-${TIMESTAMP}_dedup.txt" "./instrumentor-events-${TIMESTAMP}.txt" ./event_dictionary.txt

echo -e "${GREEN}Log processing, data structuring, and source code restoration completed successfully!${NC}"