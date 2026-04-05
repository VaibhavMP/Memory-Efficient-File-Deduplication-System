package com.dedup.deduplication.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DeduplicatedFile entity representing a file with references to stored chunks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "deduplicated_files")
@CompoundIndex(name = "user_name_idx", def = "{'ownerUserId': 1, 'originalFileName': 1}")
public class DeduplicatedFile {

    @Id
    private String id;

    /**
     * Original file name uploaded by user
     */
    private String originalFileName;

    /**
     * Unique file identifier
     */
    @Indexed(unique = true)
    private String fileUUID;

    /**
     * User who owns this file
     */
    @Indexed
    private String ownerUserId;

    /**
     * MIME type of the file
     */
    private String contentType;

    /**
     * Original file size in bytes
     */
    private Long originalSize;

    /**
     * Actual storage used after deduplication
     */
    private Long actualStorageUsed;

    /**
     * Storage saved by deduplication
     */
    private Long storageSaved;

    /**
     * Deduplication ratio (0.0 to 1.0)
     */
    private Double deduplicationRatio;

    /**
     * SHA-256 hash of the entire file (for exact duplicate detection)
     */
    @Indexed
    private String fullFileSha256Hash;

    /**
     * MD5 hash of the entire file (for quick comparison)
     */
    private String fullFileMd5Hash;

    /**
     * List of chunk references for this file
     */
    @Builder.Default
    private List<ChunkReference> chunkReferences = new ArrayList<>();

    /**
     * Number of unique chunks in this file
     */
    private Integer uniqueChunks;

    /**
     * Number of duplicate chunks found
     */
    private Integer duplicateChunks;

    /**
     * Whether this file is identical to another (exact duplicate)
     */
    @Builder.Default
    @Builder.Default
    private boolean exactDuplicate = false;

    /**
     * ID of the original file if this is a duplicate
     */
    private String originalFileId;

    /**
     * Similar files (for AI similarity detection)
     */
    @Builder.Default
    private List<SimilarFile> similarFiles = new ArrayList<>();

    /**
     * AI-detected similarity score (0.0 to 1.0)
     */
    private Double similarityScore;

    /**
     * File status: UPLOADING, PROCESSING, DEDUPLICATED, ERROR, DELETED
     */
    @Builder.Default
    private String status = "UPLOADING";

    /**
     * Cloud sync status
     */
    @Builder.Default
    private String cloudSyncStatus = "PENDING";

    /**
     * Timestamp when sync to cloud completed
     */
    private LocalDateTime cloudSyncTimestamp;

    /**
     * File creation timestamp
     */
    @CreatedDate
    private LocalDateTime createdAt;

    /**
     * Last modification timestamp
     */
    @LastModifiedDate
    private LocalDateTime updatedAt;

    /**
     * Nested class representing a reference to a stored chunk
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChunkReference {
        private String chunkId;
        private String sha256Hash;
        private Integer sequenceNumber;
        private Long offset;
        private Long length;
        private boolean stored;
    }

    /**
     * Nested class representing a similar file
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimilarFile {
        private String fileId;
        private String fileName;
        private Double similarityScore;
        private String similarityType; // CONTENT, STRUCTURE, PATTERN
    }
}
