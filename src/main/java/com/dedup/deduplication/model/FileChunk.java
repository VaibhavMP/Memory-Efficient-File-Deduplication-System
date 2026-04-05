package com.dedup.deduplication.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * FileChunk entity representing a deduplicated data chunk.
 * Each chunk is stored only once and referenced by multiple files.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "file_chunks")
@CompoundIndex(name = "hash_type_idx", def = "{'sha256Hash': 1, 'chunkType': 1}")
public class FileChunk {

    @Id
    private String id;

    /**
     * SHA-256 hash of the chunk content - used as primary identifier
     */
    @Indexed(unique = true)
    private String sha256Hash;

    /**
     * MD5 hash for quick comparison
     */
    private String md5Hash;

    /**
     * Actual chunk data stored as Base64 or binary
     */
    private byte[] data;

    /**
     * Size of the chunk in bytes
     */
    private Long size;

    /**
     * Type of chunk: FILE, BLOCK, PAGE
     */
    @Builder.Default
    private String chunkType = "BLOCK";

    /**
     * File type this chunk belongs to (e.g., IMAGE, DOCUMENT, VIDEO)
     */
    private String contentType;

    /**
     * Number of files referencing this chunk
     */
    @Builder.Default
    private Integer referenceCount = 1;

    /**
     * List of user IDs who have this chunk
     */
    private String ownerUserId;

    /**
     * Timestamp when the chunk was created
     */
    @CreatedDate
    private LocalDateTime createdAt;

    /**
     * Rabin fingerprint value used for chunking
     */
    private Long rabinFingerprint;

    /**
     * Chunk sequence number within its original file
     */
    private Integer sequenceNumber;

    /**
     * Compression algorithm used (if any)
     */
    private String compressionAlgorithm;

    /**
     * Whether this chunk is compressed
     */
    @Builder.Default
    private boolean compressed = false;
}
