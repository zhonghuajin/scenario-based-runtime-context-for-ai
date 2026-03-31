#!/bin/bash

BASE_DIR="/tmp/programs"
ZK_DIR_NAME="apache-zookeeper-3.10.0-SNAPSHOT-bin"
BIN_DIR="$BASE_DIR/$ZK_DIR_NAME/bin"

if [ ! -d "$BIN_DIR" ]; then
  echo "Error: Zookeeper bin directory not found at $BIN_DIR"
  echo "Please run start-zkserver.sh first."
  exit 1
fi

echo "Entering Zookeeper bin directory..."
cd "$BIN_DIR"

echo "Starting Zookeeper client and connecting to local server..."
./zkCli.sh -server 127.0.0.1