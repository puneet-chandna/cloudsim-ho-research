#!/bin/bash

# Memory Leak Detection File Cleanup Script
# This script removes excessive memory leak detection files to prevent system overload

echo "=== Memory Leak Detection File Cleanup ==="
echo "Starting cleanup process..."

# Set the memory logs directory
MEMORY_DIR="logs/memory"

# Check if directory exists
if [ ! -d "$MEMORY_DIR" ]; then
    echo "Memory directory does not exist: $MEMORY_DIR"
    exit 0
fi

# Count files before cleanup
FILE_COUNT_BEFORE=$(find "$MEMORY_DIR" -type f | wc -l)
echo "Files found before cleanup: $FILE_COUNT_BEFORE"

# Remove excessive memory leak report files (keep only the latest 10)
echo "Cleaning up memory leak report files..."
find "$MEMORY_DIR" -name "memory_leak_report_*.txt" -type f | sort -r | tail -n +11 | xargs -r rm -f

# Remove heap dump files (keep only the latest 5)
echo "Cleaning up heap dump files..."
find "$MEMORY_DIR" -name "heapdump_*.hprof" -type f | sort -r | tail -n +6 | xargs -r rm -f

# Remove any other excessive files (keep only the latest 20 files total)
echo "Cleaning up other excessive files..."
find "$MEMORY_DIR" -type f | sort -r | tail -n +21 | xargs -r rm -f

# Count files after cleanup
FILE_COUNT_AFTER=$(find "$MEMORY_DIR" -type f | wc -l)
FILES_REMOVED=$((FILE_COUNT_BEFORE - FILE_COUNT_AFTER))

echo "Files removed: $FILES_REMOVED"
echo "Files remaining: $FILE_COUNT_AFTER"

# Create a summary file
SUMMARY_FILE="$MEMORY_DIR/cleanup_summary.txt"
echo "=== Memory Leak Detection Cleanup Summary ===" > "$SUMMARY_FILE"
echo "Cleanup performed: $(date)" >> "$SUMMARY_FILE"
echo "Files before cleanup: $FILE_COUNT_BEFORE" >> "$SUMMARY_FILE"
echo "Files after cleanup: $FILE_COUNT_AFTER" >> "$SUMMARY_FILE"
echo "Files removed: $FILES_REMOVED" >> "$SUMMARY_FILE"
echo "Directory: $MEMORY_DIR" >> "$SUMMARY_FILE"

echo "Cleanup completed successfully!"
echo "Summary saved to: $SUMMARY_FILE"

# Check if we need to warn about high file count
if [ "$FILE_COUNT_AFTER" -gt 50 ]; then
    echo "WARNING: Still have $FILE_COUNT_AFTER files in memory directory"
    echo "Consider running cleanup more frequently or adjusting limits"
fi

exit 0 