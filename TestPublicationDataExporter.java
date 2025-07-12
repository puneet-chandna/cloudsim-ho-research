import org.cloudbus.cloudsim.experiment.ExperimentalResult;
import org.cloudbus.cloudsim.experiment.ExperimentConfig;
import org.cloudbus.cloudsim.reporting.PublicationDataExporter;
import java.util.ArrayList;
import java.util.List;

public class TestPublicationDataExporter {
    public static void main(String[] args) {
        try {
            // Create test experimental results
            List<ExperimentalResult> results = new ArrayList<>();
            
            // Create experiment config
            ExperimentConfig config = new ExperimentConfig();
            config.setAlgorithmType("HippopotamusOptimization");
            config.setVmCount(100);
            config.setHostCount(20);
            config.setTimeoutSeconds(3600);
            
            // Create experimental result
            ExperimentalResult result = new ExperimentalResult();
            result.setExperimentId("test_experiment_run_1");
            result.setExperimentConfig(config);
            
            // Set performance metrics
            ExperimentalResult.PerformanceMetrics metrics = new ExperimentalResult.PerformanceMetrics();
            
            // Set resource utilization
            ExperimentalResult.ResourceUtilizationMetrics resourceMetrics = new ExperimentalResult.ResourceUtilizationMetrics();
            resourceMetrics.setAvgCpuUtilization(75.5);
            resourceMetrics.setAvgMemoryUtilization(60.2);
            metrics.setResourceUtilization(resourceMetrics);
            
            // Set power consumption
            ExperimentalResult.PowerConsumptionMetrics powerMetrics = new ExperimentalResult.PowerConsumptionMetrics();
            powerMetrics.setTotalPowerConsumption(1500.0);
            powerMetrics.setAvgPowerConsumption(1200.0);
            metrics.setPowerConsumption(powerMetrics);
            
            // Set SLA violations
            ExperimentalResult.SLAViolationMetrics slaMetrics = new ExperimentalResult.SLAViolationMetrics();
            slaMetrics.setTotalViolations(5);
            slaMetrics.setViolationRate(0.05);
            metrics.setSlaViolations(slaMetrics);
            
            // Set response time
            ExperimentalResult.ResponseTimeMetrics responseMetrics = new ExperimentalResult.ResponseTimeMetrics();
            responseMetrics.setAvgResponseTime(150.0);
            responseMetrics.setMinResponseTime(50.0);
            responseMetrics.setMaxResponseTime(300.0);
            metrics.setResponseTime(responseMetrics);
            
            // Set throughput
            ExperimentalResult.ThroughputMetrics throughputMetrics = new ExperimentalResult.ThroughputMetrics();
            throughputMetrics.setAvgThroughput(1000.0);
            throughputMetrics.setTotalJobsCompleted(5000);
            metrics.setThroughput(throughputMetrics);
            
            result.setPerformanceMetrics(metrics);
            result.setExecutionDurationMs(5000);
            
            results.add(result);
            
            // Test PublicationDataExporter
            PublicationDataExporter exporter = new PublicationDataExporter();
            exporter.setExperimentalResults(results);
            exporter.addMetadata("test", "value");
            
            // Test CSV export
            System.out.println("Testing CSV export...");
            var csvPath = exporter.exportToCSV();
            System.out.println("CSV exported to: " + csvPath);
            
            // Test JSON export
            System.out.println("Testing JSON export...");
            var jsonPath = exporter.exportToJSON();
            System.out.println("JSON exported to: " + jsonPath);
            
            // Test R export
            System.out.println("Testing R export...");
            var rPath = exporter.exportToR();
            System.out.println("R script exported to: " + rPath);
            
            // Test MATLAB export
            System.out.println("Testing MATLAB export...");
            var matlabPath = exporter.exportToMATLAB();
            System.out.println("MATLAB script exported to: " + matlabPath);
            
            // Test SPSS export
            System.out.println("Testing SPSS export...");
            var spssPath = exporter.exportToSPSS();
            System.out.println("SPSS syntax exported to: " + spssPath);
            
            System.out.println("All exports completed successfully!");
            
        } catch (Exception e) {
            System.err.println("Error during testing: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 