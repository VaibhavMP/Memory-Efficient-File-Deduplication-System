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

- 🔧 How It Works

The Memory Efficient File Deduplication System is designed to eliminate redundant data storage using intelligent file analysis and chunk-based deduplication.

Step-by-Step Workflow:
1.File Upload
User uploads a file through the web interface.
File metadata (name, size, type) is captured.

2.Hash Generation
System generates unique hashes using:
SHA-256 (primary integrity check)
MD5 (quick comparison)
If the hash already exists → file is marked as duplicate.

3.Bloom Filter Check (O(1))
A Bloom Filter is used for ultra-fast lookup.
Quickly predicts whether a file might exist (no expensive DB calls).

4.Chunking using Rabin Fingerprinting
File is split into content-defined chunks.
Even if a small part changes → only that chunk is stored.

5.Chunk Deduplication
Each chunk is hashed and compared.
Only unique chunks are stored.
Duplicate chunks are replaced with references.

6.Storage Layer
Unique chunks are stored once.
File structure is reconstructed using references.

7.AI Similarity Detection
Detects near-duplicate files (not just exact matches).
Useful for:
Slightly edited documents
Image/text similarity

8.Secure Access
JWT-based authentication ensures only authorized users access files.

9.Cloud Sync Simulation
Mimics real-world distributed storage systems.
Demonstrates scalability concepts.

**Massive Storage Savings**
Eliminates duplicate files and repeated data.
Especially useful for:
Cloud storage platforms
Backup systems
Enterprise file servers

👉 Example:
If 100 users upload the same 100MB file →
Without deduplication: 10GB storage used
With your system: ~100MB only

💡 Impact & Cost Efficiency
Reduces Storage Cost by eliminating duplicate data
Saves Bandwidth by avoiding repeated uploads
Improves Performance with faster file processing
Optimizes Infrastructure by minimizing storage usage

Similar techniques are used by companies like Google, Dropbox, and Amazon Web Services to save large-scale storage costs and improve efficiency.
