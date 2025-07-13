# Memory Leak Detection for CloudSim Hippopotamus Optimization

This document describes the comprehensive memory leak detection system implemented for the CloudSim Hippopotamus Optimization research framework.

## Overview

The memory leak detection system implements Oracle-recommended strategies for detecting memory leaks in Java applications, specifically tailored for optimization algorithms and research experiments.

## Components

### 1. MemoryLeakDetector
- **Location**: `src/main/java/org/cloudbus/cloudsim/util/MemoryLeakDetector.java`
- **Purpose**: Core memory monitoring and leak detection
- **Features**:
  - Real-time memory usage monitoring
  - Garbage collection analysis
  - Memory growth pattern detection
  - Heap dump triggering
  - Comprehensive reporting

### 2. MemoryLeakDetectionManager
- **Location**: `src/main/java/org/cloudbus/cloudsim/util/MemoryLeakDetectionManager.java`
- **Purpose**: High-level interface for experiment monitoring
- **Features**:
  - Experiment-specific memory tracking
  - Optimization algorithm memory analysis
  - Memory leak pattern detection
  - Resource cleanup verification

### 3. MemoryLeakTest
- **Location**: `src/main/java/org/cloudbus/cloudsim/util/MemoryLeakTest.java`
- **Purpose**: Test scenarios for memory leak detection
- **Features**:
  - Growing collections leak simulation
  - Optimization algorithm leak testing
  - Resource cleanup verification
  - Memory cleanup effectiveness testing

## Memory Leak Patterns Detected

### 1. Growing Collections
- **Pattern**: Collections that grow without bounds
- **Detection**: Monitors collection sizes and growth rates
- **Solution**: Implement size limits or cleanup mechanisms

### 2. Unclosed Resources
- **Pattern**: Resources not properly closed
- **Detection**: Tracks resource allocation vs. deallocation
- **Solution**: Use try-with-resources or explicit cleanup

### 3. Cached Objects
- **Pattern**: Objects cached without eviction
- **Detection**: Monitors cache sizes and object retention
- **Solution**: Implement LRU or time-based eviction

### 4. Event Listeners
- **Pattern**: Listeners not properly removed
- **Detection**: Tracks listener registration vs. removal
- **Solution**: Remove listeners when objects are destroyed

## Integration with Hippopotamus Optimization

The memory leak detection is integrated into the `HippopotamusOptimization` class:

```java
// Memory leak detection is automatically started
memoryLeakDetector.startExperimentMonitoring(currentExperimentId);

// Memory checks performed every 10 iterations
performMemoryLeakCheck();

// Automatic cleanup on completion
memoryLeakDetector.stopExperimentMonitoring(currentExperimentId);
```

## Usage

### 1. Automatic Detection (Recommended)
The memory leak detection is automatically enabled when running experiments:

```bash
# Run with memory leak detection
./run_memory_leak_detection.sh
```

### 2. Manual Testing
Run specific memory leak tests:

```bash
# Run all tests
java -cp target/classes org.cloudbus.cloudsim.util.MemoryLeakTest

# Run specific test
java -cp target/classes org.cloudbus.cloudsim.util.MemoryLeakTest collections
```

### 3. JVM Parameters for Memory Analysis
Use these JVM parameters for detailed memory analysis:

```bash
java -Xmx4g -Xms4g \
  -XX:+UseG1GC \
  -XX:+PrintGCDetails \
  -XX:+PrintGCTimeStamps \
  -Xloggc:logs/gc.log \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=logs/heapdump.hprof \
  -jar target/cloudsim-ho-research-1.0-SNAPSHOT.jar
```

## Configuration

### Memory Leak Detection Settings
```yaml
# In experiment_config.yaml
memory_leak_detection:
  enabled: true
  check_interval: 10  # Check every 10 iterations
  growth_threshold: 0.15  # 15% memory growth threshold
  max_snapshots: 1000
  output_directory: "logs/memory"
```

### JVM Memory Settings
```bash
# Recommended settings for memory leak detection
-Xmx8g          # Maximum heap size
-Xms4g          # Initial heap size
-XX:+UseG1GC    # Use G1 garbage collector
-XX:MaxGCPauseMillis=200  # Maximum GC pause time
```

## Monitoring and Analysis

### 1. Real-time Monitoring
The system provides real-time monitoring of:
- Heap memory usage
- Garbage collection statistics
- Memory allocation patterns
- Thread count and activity

### 2. Memory Leak Reports
Generated reports include:
- Memory usage trends
- GC efficiency analysis
- Memory leak pattern detection
- Recommendations for fixes

### 3. Log Files
- `logs/memory/application.log` - Application execution log
- `logs/memory/gc.log` - Garbage collection details
- `logs/memory/system_monitor.log` - System resource monitoring
- `logs/memory/memory_leak_report_*.txt` - Detailed memory analysis

## Common Memory Leak Scenarios in Optimization Algorithms

### 1. Population Growth
```java
// Problem: Population growing without bounds
List<Hippopotamus> population = new ArrayList<>();
for (int i = 0; i < iterations; i++) {
    population.add(new Hippopotamus()); // Never removed
}

// Solution: Implement population size limits
if (population.size() > maxPopulationSize) {
    population.remove(0); // Remove oldest
}
```

### 2. History Tracking
```java
// Problem: Unlimited history tracking
List<Double> fitnessHistory = new ArrayList<>();
fitnessHistory.add(fitness); // Grows indefinitely

// Solution: Use bounded collections
List<Double> fitnessHistory = new LinkedList<>();
if (fitnessHistory.size() > maxHistorySize) {
    fitnessHistory.removeFirst();
}
```

### 3. Temporary Data Accumulation
```java
// Problem: Temporary data not cleaned up
Map<String, Object> tempData = new HashMap<>();
tempData.put("iteration_" + i, largeObject); // Never cleared

// Solution: Clear temporary data
tempData.clear(); // After each iteration
```

## Best Practices

### 1. Memory-Aware Programming
- Use bounded collections for history tracking
- Implement proper cleanup in iteration loops
- Use object pools for frequently created objects
- Implement memory-aware termination conditions

### 2. Resource Management
- Use try-with-resources for file/network operations
- Implement proper cleanup in finally blocks
- Use weak references for caching mechanisms
- Monitor resource usage patterns

### 3. Optimization Algorithm Design
- Limit population sizes
- Implement history cleanup mechanisms
- Use memory-efficient data structures
- Add memory pressure checks

## Troubleshooting

### 1. High Memory Usage
```bash
# Check current memory usage
jstat -gc <pid>

# Generate heap dump
jmap -dump:format=b,file=heapdump.hprof <pid>

# Analyze heap dump with MAT or similar tool
```

### 2. Memory Leak Detection
```bash
# Run memory leak detection
./run_memory_leak_detection.sh

# Check generated reports
cat logs/memory/memory_leak_report_*.txt
```

### 3. Performance Impact
- Memory leak detection adds minimal overhead (~1-2%)
- Monitoring can be disabled for production runs
- Use sampling-based monitoring for high-performance scenarios

## Integration with CI/CD

### 1. Automated Testing
```yaml
# In CI pipeline
- name: Memory Leak Test
  run: |
    java -cp target/classes org.cloudbus.cloudsim.util.MemoryLeakTest
    ./run_memory_leak_detection.sh
```

### 2. Memory Thresholds
```yaml
# Fail build if memory usage exceeds threshold
- name: Check Memory Usage
  run: |
    if grep -q "MEMORY LEAK DETECTED" logs/memory/application.log; then
      echo "Memory leak detected - build failed"
      exit 1
    fi
```

## Future Enhancements

### 1. Advanced Detection
- Machine learning-based leak pattern detection
- Predictive memory leak analysis
- Integration with profiling tools

### 2. Performance Optimization
- Sampling-based monitoring for high-performance scenarios
- Adaptive monitoring intervals
- Memory usage prediction

### 3. Visualization
- Real-time memory usage graphs
- Memory leak trend analysis
- Interactive memory analysis dashboard

## References

1. [Oracle Memory Leak Detection Guide](https://docs.oracle.com/javase/8/docs/technotes/guides/troubleshoot/memleaks002.html)
2. [Java Memory Management Best Practices](https://docs.oracle.com/javase/8/docs/technotes/guides/vm/gctuning/)
3. [G1 Garbage Collector Tuning](https://docs.oracle.com/javase/8/docs/technotes/guides/vm/gctuning/g1_gc_tuning.html)

## Support

For issues or questions about memory leak detection:
1. Check the generated log files in `logs/memory/`
2. Review the memory leak reports
3. Use the provided test utilities
4. Consult the troubleshooting section above 