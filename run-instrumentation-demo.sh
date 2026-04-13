#!/bin/bash

# 默认参数
TARGET_FOLDERS_FILE="./target-folders.txt"
TARGET_FOLDERS=()
SKIP_BUILD_AND_TEST=false

# 颜色定义
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# 解析命令行参数
while [[ "$#" -gt 0 ]]; do
    case $1 in
        --target-folders-file) TARGET_FOLDERS_FILE="$2"; shift ;;
        --target-folders) 
            # 将逗号或空格分隔的字符串转换为数组
            IFS=', ' read -r -a TARGET_FOLDERS <<< "$2"
            shift ;;
        --skip-build-and-test) SKIP_BUILD_AND_TEST=true ;;
        *) echo -e "${RED}Unknown parameter passed: $1${NC}"; exit 1 ;;
    esac
    shift
done

# 如果没有通过参数指定 TargetFolders，则从文件中读取
if [ ${#TARGET_FOLDERS[@]} -eq 0 ]; then
    if [ ! -f "$TARGET_FOLDERS_FILE" ]; then
        echo -e "${RED}Error: Target folders file does not exist: $TARGET_FOLDERS_FILE${NC}"
        exit 1
    fi
    
    while IFS= read -r line || [ -n "$line" ]; do
        # 去除首尾空白字符
        line=$(echo "$line" | xargs)
        # 忽略空行和以 # 开头的注释行
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

# 验证目标文件夹是否存在
for folder in "${TARGET_FOLDERS[@]}"; do
    if [ ! -d "$folder" ]; then
        echo -e "${RED}Error: Target folder does not exist: $folder${NC}"
        exit 1
    fi
done

# 打印目标文件夹
printf -v joined_folders "%s, " "${TARGET_FOLDERS[@]}"
echo -e "${YELLOW}Target folders: ${joined_folders%, }${NC}"

# 1. 恢复源代码
echo -e "${CYAN}Restoring the instrumented source folders using Git...${NC}"
for folder in "${TARGET_FOLDERS[@]}"; do
    git restore "$folder"
    git clean -fd "$folder"
done

# 2. 检查 Java 环境变量
echo -e "${CYAN}Checking Java environment variables...${NC}"
if [ -z "$JAVA_HOME" ]; then
    echo -e "${RED}Error: JAVA_HOME Environment variable not configured. Please set JAVA_HOME to point to your JDK installation directory.${NC}"
    exit 1
fi
echo -e "${GREEN}Using JAVA_HOME: $JAVA_HOME${NC}"
export PATH="$JAVA_HOME/bin:$PATH"

# 3. 第一次构建
echo -e "${CYAN}Executing mvn clean package to build the instrumentor...${NC}"
mvn -f ./demos/pom.xml clean package -DskipTests
if [ $? -ne 0 ]; then
    echo -e "${RED}Maven build failed${NC}"
    exit 1
fi

# 4. 执行插桩相关的 Java 命令
echo -e "${CYAN}Executing code instrumentation (Instrumentor)...${NC}"

# 4.1 Main instrumentation
java -jar ./demos/instrumentor/target/instrumentor-1.0-SNAPSHOT.jar "${TARGET_FOLDERS[@]}"
if [ $? -ne 0 ]; then
    echo -e "${YELLOW}Warning: Main instrumentation step returned non-zero exit code: $?${NC}"
fi

# 4.2 Encoding mapping
java -jar ./demos/instrumentor-with-encoding/target/instrumentor-with-encoding-1.0-SNAPSHOT.jar "${TARGET_FOLDERS[@]}"
if [ $? -ne 0 ]; then
    echo -e "${YELLOW}Warning: Encoding mapping step returned non-zero exit code: $?${NC}"
fi

# 4.3 Activator
java -jar ./demos/instrumentor-activator/target/instrumentor-activator-1.0-SNAPSHOT.jar "${TARGET_FOLDERS[@]}"
if [ $? -ne 0 ]; then
    echo -e "${YELLOW}Warning: Activator step returned non-zero exit code: $?${NC}"
fi

# 5 & 6. 第二次构建与测试
if [ "$SKIP_BUILD_AND_TEST" = false ]; then
    echo -e "${CYAN}Executing mvn clean package again...${NC}"
    mvn -f ./demos/pom.xml clean package -DskipTests
    if [ $? -ne 0 ]; then
        echo -e "${RED}Second Maven build failed${NC}"
        exit 1
    fi

    echo -e "${CYAN}Executing SyncTest...${NC}"
    java -cp ./demos/instrumentor-test/target/instrumentor-test-1.0-SNAPSHOT.jar com.example.instrumentor.happens.before.SyncTest
    if [ $? -ne 0 ]; then
        echo -e "${YELLOW}Warning: Test execution returned non-zero exit code: $?${NC}"
    fi
else
    echo -e "${YELLOW}Skipping second build and test execution (SkipBuildAndTest flag is set)${NC}"
fi

echo -e "${GREEN}Instrumentation and testing phase completed. Please check the generated log file timestamp and use process-logs-demo.sh for subsequent processing.${NC}"