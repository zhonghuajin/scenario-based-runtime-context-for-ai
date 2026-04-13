#!/bin/bash

# 默认参数
TARGET_FOLDERS_FILE="./target-folders.txt"
TARGET_FOLDERS=()
LOG_FILE=""
COMMENT_MAPPING_FILE=""
PRUNED_FOLDER="pruned/"
EVENTS_FILE=""
EVENT_DICTIONARY_FILE="event_dictionary.txt"
BLOCK_PRUNER_JAR="./demos/block-pruner/target/block-pruner-1.0-SNAPSHOT.jar"
DATA_STRUCTURING_JAR="./demos/data-structuring/target/data-structuring-1.0-SNAPSHOT.jar"

# 颜色定义
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
RED='\033[0;31m'
DARK_GRAY='\033[1;30m'
NC='\033[0m' # No Color

# 解析命令行参数
while [[ "$#" -gt 0 ]]; do
    case $1 in
        --target-folders-file) TARGET_FOLDERS_FILE="$2"; shift ;;
        --target-folders) 
            IFS=', ' read -r -a TARGET_FOLDERS <<< "$2"
            shift ;;
        --log-file) LOG_FILE="$2"; shift ;;
        --comment-mapping-file) COMMENT_MAPPING_FILE="$2"; shift ;;
        --pruned-folder) PRUNED_FOLDER="$2"; shift ;;
        --events-file) EVENTS_FILE="$2"; shift ;;
        --event-dictionary-file) EVENT_DICTIONARY_FILE="$2"; shift ;;
        --block-pruner-jar) BLOCK_PRUNER_JAR="$2"; shift ;;
        --data-structuring-jar) DATA_STRUCTURING_JAR="$2"; shift ;;
        *) echo -e "${RED}Unknown parameter passed: $1${NC}"; exit 1 ;;
    esac
    shift
done

# 检查必填参数
if [ -z "$LOG_FILE" ] || [ -z "$COMMENT_MAPPING_FILE" ] || [ -z "$EVENTS_FILE" ]; then
    echo -e "${RED}Error: --log-file, --comment-mapping-file, and --events-file are mandatory parameters.${NC}"
    exit 1
fi

# 解析目标文件夹
if [ ${#TARGET_FOLDERS[@]} -eq 0 ]; then
    if [ ! -f "$TARGET_FOLDERS_FILE" ]; then
        echo -e "${RED}Error: Target folders file does not exist: $TARGET_FOLDERS_FILE${NC}"
        exit 1
    fi
    
    while IFS= read -r line || [ -n "$line" ]; do
        line=$(echo "$line" | xargs)
        if [[ -n "$line" && ! "$line" == \#* ]]; then
            TARGET_FOLDERS+=("$line")
        fi
    done < "$TARGET_FOLDERS_FILE"

    if [ ${#TARGET_FOLDERS[@]} -eq 0 ]; then
        echo -e "${RED}Error: No target folders found in file: $TARGET_FOLDERS_FILE${NC}"
        exit 1
    fi
    echo -e "${YELLOW}Loaded ${#TARGET_FOLDERS[@]} target folder(s) from file: $TARGET_FOLDERS_FILE${NC}"
fi

for folder in "${TARGET_FOLDERS[@]}"; do
    if [ ! -d "$folder" ]; then
        echo -e "${RED}Error: Target folder does not exist: $folder${NC}"
        exit 1
    fi
done

printf -v joined_folders "%s, " "${TARGET_FOLDERS[@]}"
echo -e "${YELLOW}Target folders: ${joined_folders%, }${NC}"

# 验证关键输入文件是否存在
if [ ! -f "$LOG_FILE" ]; then
    echo -e "${RED}Error: Log file does not exist: $LOG_FILE${NC}"
    exit 1
fi

if [ ! -f "$COMMENT_MAPPING_FILE" ]; then
    echo -e "${RED}Error: Comment mapping file does not exist: $COMMENT_MAPPING_FILE${NC}"
    exit 1
fi

# 检查 Java 环境变量
echo -e "${CYAN}Checking Java environment variables...${NC}"
if [ -z "$JAVA_HOME" ]; then
    echo -e "${RED}Error: JAVA_HOME Environment variable not configured. Please set JAVA_HOME to point to your JDK installation directory.${NC}"
    exit 1
fi
echo -e "${GREEN}Using JAVA_HOME: $JAVA_HOME${NC}"
export PATH="$JAVA_HOME/bin:$PATH"

# 打印配置信息
echo -e "${CYAN}Starting log processing and data structuring...${NC}"
printf -v joined_folders_semi "%s; " "${TARGET_FOLDERS[@]}"
echo -e "${DARK_GRAY}  TargetFolders:      ${joined_folders_semi%; }${NC}"
echo -e "${DARK_GRAY}  LogFile:            $LOG_FILE${NC}"
echo -e "${DARK_GRAY}  CommentMappingFile: $COMMENT_MAPPING_FILE${NC}"
echo -e "${DARK_GRAY}  PrunedFolder:       $PRUNED_FOLDER${NC}"
echo -e "${DARK_GRAY}  EventsFile:         $EVENTS_FILE${NC}"
echo -e "${DARK_GRAY}  EventDictionaryFile:$EVENT_DICTIONARY_FILE${NC}"

# 1. 恢复源代码
echo -e "${CYAN}Restoring the instrumented source folders using Git...${NC}"
for folder in "${TARGET_FOLDERS[@]}"; do
    git restore "$folder"
    git clean -fd "$folder"
done

# 2. Block Pruner — 将多个源目录用分号连接
echo -e "${YELLOW}Executing Block Pruner...${NC}"
SOURCE_DIRS_ARG=$(IFS=\;; echo "${TARGET_FOLDERS[*]}")
java -jar "$BLOCK_PRUNER_JAR" "$SOURCE_DIRS_ARG" "$COMMENT_MAPPING_FILE" "$LOG_FILE" "$PRUNED_FOLDER"

# 3. Data Structuring
echo -e "${YELLOW}Executing Data Structuring...${NC}"
java -jar "$DATA_STRUCTURING_JAR" "$PRUNED_FOLDER" "$COMMENT_MAPPING_FILE" "$LOG_FILE" "$EVENTS_FILE" "$EVENT_DICTIONARY_FILE"

echo -e "${GREEN}Log processing, data structuring, and source code restoration completed successfully!${NC}"