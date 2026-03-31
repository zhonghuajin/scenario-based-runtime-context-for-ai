**File 2: `demos/zookeeper-test/start-zkserver.sh`** (Linux-compatible startup script)
```bash
#!/bin/bash
set -e

# Ensure ZK_HOME is set
if [ -z "$ZK_HOME" ]; then
  echo "Error: ZK_HOME environment variable is not set."
  echo "Please set it to your ZooKeeper source root directory."
  exit 1
fi

BASE_DIR="/tmp/programs"
ZK_DIR_NAME="apache-zookeeper-3.10.0-SNAPSHOT-bin"
ZK_DIR_PATH="$BASE_DIR/$ZK_DIR_NAME"
TARBALL_PATH="$ZK_HOME/zookeeper-assembly/target/apache-zookeeper-3.10.0-SNAPSHOT-bin.tar.gz"

echo -e "\e[32m1. Creating and switching to working directory: $BASE_DIR\e[0m"
mkdir -p "$BASE_DIR"
cd "$BASE_DIR"

echo -e "\e[32m2. Checking and cleaning up old version directory...\e[0m"
if [ -d "$ZK_DIR_PATH" ]; then
    rm -rf "$ZK_DIR_PATH"
    echo "   Old directory deleted."
fi

echo -e "\e[32m3. Extracting the latest Zookeeper package...\e[0m"
if [ ! -f "$TARBALL_PATH" ]; then
    echo "Error: Tarball not found at $TARBALL_PATH. Did you run 'mvn clean install'?"
    exit 1
fi
tar -xzf "$TARBALL_PATH"

echo -e "\e[32m4. Copying zoo.cfg configuration file...\e[0m"
CONF_DIR="$ZK_DIR_PATH/conf"
# Assuming zoo.cfg is prepared in the ZK_HOME/conf or current dir
if [ -f "$ZK_HOME/conf/zoo.cfg" ]; then
    cp "$ZK_HOME/conf/zoo.cfg" "$CONF_DIR/"
else
    echo "Warning: zoo.cfg not found in $ZK_HOME/conf/. Please ensure it is created."
fi

echo -e "\e[32m5. Starting Zookeeper...\e[0m"
cd "$ZK_DIR_PATH/bin"

# Start Zookeeper service in the foreground
./zkServer.sh start-foreground