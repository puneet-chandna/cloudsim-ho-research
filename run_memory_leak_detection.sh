#!/bin/bash

# Memory Leak Detection Script for CloudSim Hippopotamus Optimization
# This script runs the application with memory leak detection enabled

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
JAR_FILE="target/cloudsim-ho-research-1.0-SNAPSHOT.jar"
LOG_DIR="logs/memory"
HEAP_SIZE="4g"
STACK_SIZE="2m"
GC_LOG_FILE="${LOG_DIR}/gc.log"

# Create log directory
mkdir -p "$LOG_DIR"

echo -e "${BLUE}=== CloudSim Memory Leak Detection ===${NC}"
echo "JAR File: $JAR_FILE"
echo "Log Directory: $LOG_DIR"
echo "Heap Size: $HEAP_SIZE"
echo ""

# Check if JAR exists
if [ ! -f "$JAR_FILE" ]; then
    echo -e "${RED}Error: JAR file not found: $JAR_FILE${NC}"
    echo "Please build the project first: mvn clean package"
    exit 1
fi

# Check available memory
TOTAL_MEM=$(free -m | awk 'NR==2{printf "%.0f", $2}')
AVAILABLE_MEM=$(free -m | awk 'NR==2{printf "%.0f", $7}')
echo -e "${YELLOW}System Memory:${NC}"
echo "  Total: ${TOTAL_MEM}MB"
echo "  Available: ${AVAILABLE_MEM}MB"
echo ""

# JVM parameters for memory leak detection
JVM_OPTS=(
    "-Xmx${HEAP_SIZE}"
    "-Xms${HEAP_SIZE}"
    "-Xss${STACK_SIZE}"
    "-XX:+UseG1GC"
    "-XX:MaxGCPauseMillis=200"
    "-XX:+PrintGCDetails"
    "-XX:+PrintGCTimeStamps"
    "-XX:+PrintGCDateStamps"
    "-Xloggc:${GC_LOG_FILE}"
    "-XX:+UseGCLogFileRotation"
    "-XX:NumberOfGCLogFiles=5"
    "-XX:GCLogFileSize=10M"
    "-XX:+HeapDumpOnOutOfMemoryError"
    "-XX:HeapDumpPath=${LOG_DIR}/heapdump.hprof"
    "-XX:+PrintTenuringDistribution"
    "-XX:+PrintGCApplicationStoppedTime"
    "-Djava.rmi.server.hostname=localhost"
    "-Dcom.sun.management.jmxremote"
    "-Dcom.sun.management.jmxremote.port=9999"
    "-Dcom.sun.management.jmxremote.authenticate=false"
    "-Dcom.sun.management.jmxremote.ssl=false"
    "-Djava.util.logging.config.file=src/main/resources/config/logging_config.xml"
)

# Application arguments
APP_ARGS=(
    "--experiment" "single"
    "--algorithm" "HippopotamusOptimization"
    "--dataset" "synthetic_workloads"
    "--memory-leak-detection" "true"
)

echo -e "${GREEN}Starting application with memory leak detection...${NC}"
echo "JVM Options: ${JVM_OPTS[*]}"
echo "Application Args: ${APP_ARGS[*]}"
echo ""

# Start monitoring in background
echo -e "${YELLOW}Starting system monitoring...${NC}"
(
    while true; do
        echo "$(date '+%Y-%m-%d %H:%M:%S') - Memory: $(free -m | awk 'NR==2{printf "%.0f%%", $3*100/$2}')" >> "${LOG_DIR}/system_monitor.log"
        sleep 5
    done
) &
MONITOR_PID=$!

# Function to cleanup on exit
cleanup() {
    echo -e "\n${YELLOW}Cleaning up...${NC}"
    kill $MONITOR_PID 2>/dev/null || true
    echo -e "${GREEN}Memory leak detection completed.${NC}"
    echo "Check logs in: $LOG_DIR"
}

# Set trap to cleanup on script exit
trap cleanup EXIT

# Run the application
echo -e "${GREEN}Running CloudSim application...${NC}"
java "${JVM_OPTS[@]}" -jar "$JAR_FILE" "${APP_ARGS[@]}" 2>&1 | tee "${LOG_DIR}/application.log"

echo -e "${GREEN}Application completed.${NC}"
echo ""
echo -e "${BLUE}=== Memory Leak Detection Summary ===${NC}"
echo "Log files generated:"
echo "  - Application log: ${LOG_DIR}/application.log"
echo "  - GC log: ${GC_LOG_FILE}"
echo "  - System monitor: ${LOG_DIR}/system_monitor.log"
echo "  - Memory leak reports: ${LOG_DIR}/memory_leak_report_*.txt"
echo ""

# Analyze results if available
if [ -f "${LOG_DIR}/application.log" ]; then
    echo -e "${YELLOW}=== Quick Analysis ===${NC}"
    
    # Check for memory-related errors
    if grep -q "OutOfMemoryError\|Memory\|heap" "${LOG_DIR}/application.log"; then
        echo -e "${RED}Memory-related errors detected!${NC}"
        grep -i "memory\|heap\|outofmemory" "${LOG_DIR}/application.log" | tail -5
    else
        echo -e "${GREEN}No memory-related errors detected.${NC}"
    fi
    
    # Check for memory leak warnings
    if grep -q "MEMORY LEAK\|memory leak" "${LOG_DIR}/application.log"; then
        echo -e "${RED}Memory leak warnings detected!${NC}"
        grep -i "memory leak" "${LOG_DIR}/application.log" | tail -5
    else
        echo -e "${GREEN}No memory leak warnings detected.${NC}"
    fi
fi

echo ""
echo -e "${BLUE}For detailed analysis, check the log files in: $LOG_DIR${NC}" 