package com.example;

import java.util.Arrays;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.distribution.CountAtBucket;

@SpringBootApplication
public class ReproducerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReproducerApplication.class, args);
    }

    @Bean
    public CommandLineRunner verifyBug(MeterRegistry registry) {
        return args -> {
            // 1. Register a DistributionSummary named "example"
            DistributionSummary summary = DistributionSummary.builder("example")
                    .register(registry);

            // 2. Record a test value to generate snapshot data
            summary.record(1.5);

            // 3. Get histogram bucket information
            CountAtBucket[] buckets = summary.takeSnapshot().histogramCounts();

            System.out.println("\n==================================================");
            System.out.println("Testing Spring Boot Issue #50021");
            System.out.println("Config file content: management.metrics.distribution.slo.example=1, 2, 3");
            System.out.println("Expected SLO boundaries: [1.0, 2.0, 3.0]");
            
            if (buckets.length == 0) {
                System.out.println("Actual SLO boundaries: []");
                System.out.println("Conclusion: BUG successfully reproduced! Integer configuration is completely ignored.");
            } else {
                System.out.println("Actual SLO boundaries: " + Arrays.toString(buckets));
                System.out.println("Conclusion: BUG does not exist or has been fixed.");
            }
            System.out.println("==================================================\n");
        };
    }
}