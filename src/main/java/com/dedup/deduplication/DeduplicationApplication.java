package com.dedup.deduplication;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for the Memory Efficient File Deduplication System.
 * 
 * This system detects duplicate files/data chunks using:
 * - SHA-256 and MD5 hashing
 * - Rabin Fingerprinting for content-defined chunking
 * - Bloom Filters for fast duplicate lookup
 * - AI-based similarity detection
 * 
 * Features:
 * - Multi-user support with JWT authentication
 * - Cloud sync simulation
 * - Memory savings tracking
 * - MongoDB storage backend
 */
@SpringBootApplication
@EnableMongoAuditing
@EnableAsync
@EnableScheduling
public class DeduplicationApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeduplicationApplication.class, args);
    }
}
