# Memory Efficient File Deduplication System

A system that detects duplicate files, stores only one copy, and replaces duplicates with references. Built with Java Spring Boot.

## Features

- **Hash-based Detection**: SHA-256 and MD5 hashing
- **Rabin Fingerprinting**: Content-defined chunking
- **Bloom Filters**: Fast O(1) duplicate lookup
- **Chunk-level Deduplication**
- **JWT Authentication**
- **AI-based Similarity Detection**
- **Cloud Sync Simulation**

## Quick Start

1. Run `1-INSTALL.bat` to install Java 17 and Maven
2. Run `setup.bat` to start the application
3. Open http://localhost:8080
4. Register a new account and start uploading files

## Technology Stack

- Java 17 + Spring Boot 3.2
- In-memory storage (MongoDB disabled for standalone)
- Spring Security + JWT
- Guava (Bloom Filters)
- Apache Tika (Content Analysis)