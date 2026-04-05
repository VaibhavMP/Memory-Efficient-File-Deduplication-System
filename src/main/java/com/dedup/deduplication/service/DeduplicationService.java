package com.dedup.deduplication.service;

import com.dedup.deduplication.model.DeduplicatedFile;
import com.dedup.deduplication.model.DeduplicatedFile.ChunkReference;
import com.dedup.deduplication.model.DeduplicatedFile.SimilarFile;
import com.dedup.deduplication.model.FileChunk;
import com.dedup.deduplication.model.User;
import com.dedup.deduplication.repository.InMemoryDeduplicatedFileRepository;
import com.dedup.deduplication.repository.InMemoryFileChunkRepository;
import com.dedup.deduplication.repository.InMemoryUserRepository;
import com.dedup.deduplication.util.BloomFilterUtil;
import com.dedup.deduplication.util.HashingUtil;
import com.dedup.deduplication.util.RabinFingerprinting;
import com.dedup.deduplication.util.RabinFingerprinting.Chunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Core deduplication service implementing:
 * - SHA-256 and MD5 hashing for exact duplicate detection
 * - Rabin fingerprinting for content-defined chunking
 * - Bloom filter for fast duplicate lookup
 * - Chunk-level deduplication
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeduplicationService {

    private final HashingUtil hashingUtil;
    private final RabinFingerprinting rabinFingerprinting;
    private final BloomFilterUtil bloomFilterUtil;
    private final InMemoryFileChunkRepository fileChunkRepository;
    private final InMemoryDeduplicatedFileRepository deduplicatedFileRepository;
    private final InMemoryUserRepository userRepository;
    private final AISimilarityService aiSimilarityService;

    /**
     * Process a file upload and apply deduplication.
     *
     * @param file     the uploaded file
     * @param userId   the user ID
     * @return processing result
     */
    public DeduplicationResult processFile(MultipartFile file, String userId) throws IOException {
        log.info("Processing file: {} for user: {}", file.getOriginalFilename(), userId);

        // Read file data
        byte[] fileData = file.getBytes();
        long originalSize = fileData.length;

        // Compute full file hash first (for exact duplicate detection)
        String fullFileSha256 = hashingUtil.computeSHA256(fileData);
        String fullFileMd5 = hashingUtil.computeMD5(fileData);

        // Check for exact duplicate using bloom filter first (fast path)
        if (bloomFilterUtil.mightExistAnywhere(userId, fullFileSha256)) {
            // Verify with database
            Optional<DeduplicatedFile> existingFile = deduplicatedFileRepository
                    .findByFullFileSha256Hash(fullFileSha256)
                    .stream()
                    .filter(f -> !f.isExactDuplicate())
                    .findFirst();

            if (existingFile.isPresent()) {
                return handleExactDuplicate(file, userId, existingFile.get(), originalSize);
            }
        }

        // Perform chunk-level deduplication
        return performChunkDeduplication(file, fileData, userId, fullFileSha256, fullFileMd5, originalSize);
    }

    /**
     * Process file from input stream.
     *
     * @param inputStream the file input stream
     * @param fileName    the file name
     * @param contentType the content type
     * @param userId      the user ID
     * @return processing result
     */
    public DeduplicationResult processFileFromStream(InputStream inputStream, String fileName,
                                                      String contentType, String userId) throws IOException {
        log.info("Processing file stream: {} for user: {}", fileName, userId);

        // Read all data from stream
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int bytesRead;
        while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, bytesRead);
        }
        byte[] fileData = buffer.toByteArray();
        long originalSize = fileData.length;

        // Compute hashes
        String fullFileSha256 = hashingUtil.computeSHA256(fileData);
        String fullFileMd5 = hashingUtil.computeMD5(fileData);

        // Check for exact duplicate
        if (bloomFilterUtil.mightExistAnywhere(userId, fullFileSha256)) {
            Optional<DeduplicatedFile> existingFile = deduplicatedFileRepository
                    .findByFullFileSha256Hash(fullFileSha256)
                    .stream()
                    .filter(f -> !f.isExactDuplicate())
                    .findFirst();

            if (existingFile.isPresent()) {
                return handleExactDuplicate(fileName, contentType, userId, existingFile.get(), originalSize);
            }
        }

        // Perform chunk deduplication
        return performChunkDeduplication(fileName, contentType, fileData, userId, fullFileSha256, fullFileMd5, originalSize);
    }

    private DeduplicationResult handleExactDuplicate(MultipartFile file, String userId,
                                                      DeduplicatedFile originalFile, long originalSize) {
        DeduplicationResult result = new DeduplicationResult();
        result.setFileUUID(UUID.randomUUID().toString());
        result.setOriginalFileName(file.getOriginalFilename());
        result.setContentType(file.getContentType());
        result.setOriginalSize(originalSize);
        result.setActualStorageUsed(0L); // No additional storage
        result.setStorageSaved(originalSize);
        result.setDeduplicationRatio(1.0);
        result.setExactDuplicate(true);
        result.setOriginalFileId(originalFile.getId());
        result.setStatus("DEDUPLICATED");

        // Create reference file entry
        DeduplicatedFile duplicateFile = DeduplicatedFile.builder()
                .fileUUID(result.getFileUUID())
                .originalFileName(file.getOriginalFilename())
                .contentType(file.getContentType())
                .ownerUserId(userId)
                .originalSize(originalSize)
                .actualStorageUsed(0L)
                .storageSaved(originalSize)
                .deduplicationRatio(1.0)
                .fullFileSha256Hash(hashingUtil.computeSHA256(file.getBytes()))
                .fullFileMd5Hash(hashingUtil.computeMD5(file.getBytes()))
                .isExactDuplicate(true)
                .originalFileId(originalFile.getId())
                .status("DEDUPLICATED")
                .chunkReferences(new ArrayList<>())
                .similarFiles(new ArrayList<>())
                .build();

        deduplicatedFileRepository.save(duplicateFile);
        updateUserStats(userId, originalSize, 0L, originalSize, true);

        log.info("Exact duplicate detected for file: {}", file.getOriginalFilename());
        return result;
    }

    private DeduplicationResult handleExactDuplicate(String fileName, String contentType,
                                                      String userId, DeduplicatedFile originalFile, long originalSize) {
        DeduplicationResult result = new DeduplicationResult();
        result.setFileUUID(UUID.randomUUID().toString());
        result.setOriginalFileName(fileName);
        result.setContentType(contentType);
        result.setOriginalSize(originalSize);
        result.setActualStorageUsed(0L);
        result.setStorageSaved(originalSize);
        result.setDeduplicationRatio(1.0);
        result.setExactDuplicate(true);
        result.setOriginalFileId(originalFile.getId());
        result.setStatus("DEDUPLICATED");

        DeduplicatedFile duplicateFile = DeduplicatedFile.builder()
                .fileUUID(result.getFileUUID())
                .originalFileName(fileName)
                .contentType(contentType)
                .ownerUserId(userId)
                .originalSize(originalSize)
                .actualStorageUsed(0L)
                .storageSaved(originalSize)
                .deduplicationRatio(1.0)
                .isExactDuplicate(true)
                .originalFileId(originalFile.getId())
                .status("DEDUPLICATED")
                .chunkReferences(new ArrayList<>())
                .similarFiles(new ArrayList<>())
                .build();

        deduplicatedFileRepository.save(duplicateFile);
        updateUserStats(userId, originalSize, 0L, originalSize, true);

        return result;
    }

    private DeduplicationResult performChunkDeduplication(MultipartFile file, byte[] fileData,
                                                           String userId, String fullFileSha256,
                                                           String fullFileMd5, long originalSize) throws IOException {
        return performChunkDeduplication(
                file.getOriginalFilename(),
                file.getContentType(),
                fileData,
                userId,
                fullFileSha256,
                fullFileMd5,
                originalSize
        );
    }

    private DeduplicationResult performChunkDeduplication(String fileName, String contentType,
                                                           byte[] fileData, String userId,
                                                           String fullFileSha256, String fullFileMd5,
                                                           long originalSize) {
        String fileUUID = UUID.randomUUID().toString();

        // Perform Rabin fingerprinting to create chunks
        List<Chunk> rabinChunks = rabinFingerprinting.chunkData(fileData);
        log.info("Created {} chunks using Rabin fingerprinting", rabinChunks.size());

        List<ChunkReference> chunkReferences = new ArrayList<>();
        List<FileChunk> newChunks = new ArrayList<>();
        long actualStorageUsed = 0;
        int duplicateChunks = 0;
        int storedChunks = 0;

        // Process each chunk
        for (int i = 0; i < rabinChunks.size(); i++) {
            Chunk rabinChunk = rabinChunks.get(i);
            byte[] chunkData = rabinChunk.data();
            String chunkSha256 = hashingUtil.computeSHA256(chunkData);
            String chunkMd5 = hashingUtil.computeMD5(chunkData);

            ChunkReference reference = new ChunkReference();
            reference.setSha256Hash(chunkSha256);
            reference.setSequenceNumber(i);
            reference.setOffset((long) rabinChunk.start());
            reference.setLength((long) rabinChunk.length());

            // Check if chunk exists (bloom filter first)
            if (bloomFilterUtil.mightExistAnywhere(userId, chunkSha256)) {
                // Verify in database
                Optional<FileChunk> existingChunk = fileChunkRepository.findBySha256Hash(chunkSha256);
                if (existingChunk.isPresent()) {
                    reference.setChunkId(existingChunk.get().getId());
                    reference.setStored(false);
                    duplicateChunks++;
                    log.debug("Duplicate chunk found: {}", chunkSha256);
                    continue;
                }
            }

            // Store new chunk
            FileChunk fileChunk = FileChunk.builder()
                    .sha256Hash(chunkSha256)
                    .md5Hash(chunkMd5)
                    .data(chunkData)
                    .size((long) chunkData.length)
                    .chunkType("BLOCK")
                    .contentType(contentType)
                    .referenceCount(1)
                    .ownerUserId(userId)
                    .rabinFingerprint(rabinChunk.fingerprint())
                    .sequenceNumber(i)
                    .build();

            newChunks.add(fileChunk);
            reference.setChunkId(null); // Will be set after save
            reference.setStored(true);
            actualStorageUsed += chunkData.length;
            storedChunks++;

            chunkReferences.add(reference);
        }

        // Save new chunks to database
        if (!newChunks.isEmpty()) {
            List<FileChunk> savedChunks = fileChunkRepository.saveAll(newChunks);
            
            // Update chunk references with saved IDs
            for (int i = 0; i < savedChunks.size(); i++) {
                FileChunk saved = savedChunks.get(i);
                for (ChunkReference ref : chunkReferences) {
                    if (ref.getSha256Hash().equals(saved.getSha256Hash()) && ref.isStored()) {
                        ref.setChunkId(saved.getId());
                        break;
                    }
                }
                // Add to bloom filter
                bloomFilterUtil.addToUserFilter(userId, saved.getSha256Hash());
                bloomFilterUtil.addToGlobalFilter(saved.getSha256Hash());
            }
        }

        // Calculate deduplication ratio
        long storageSaved = originalSize - actualStorageUsed;
        double deduplicationRatio = originalSize > 0 ? (double) storageSaved / originalSize : 0.0;

        // Create deduplicated file record
        DeduplicatedFile deduplicatedFile = DeduplicatedFile.builder()
                .fileUUID(fileUUID)
                .originalFileName(fileName)
                .contentType(contentType)
                .ownerUserId(userId)
                .originalSize(originalSize)
                .actualStorageUsed(actualStorageUsed)
                .storageSaved(storageSaved)
                .deduplicationRatio(deduplicationRatio)
                .fullFileSha256Hash(fullFileSha256)
                .fullFileMd5Hash(fullFileMd5)
                .chunkReferences(chunkReferences)
                .uniqueChunks(storedChunks)
                .duplicateChunks(duplicateChunks)
                .isExactDuplicate(false)
                .status("DEDUPLICATED")
                .cloudSyncStatus("PENDING")
                .similarFiles(new ArrayList<>())
                .build();

        deduplicatedFileRepository.save(deduplicatedFile);

        // Update bloom filter with full file hash
        bloomFilterUtil.addToUserFilter(userId, fullFileSha256);
        bloomFilterUtil.addToGlobalFilter(fullFileSha256);

        // Update user statistics
        updateUserStats(userId, originalSize, actualStorageUsed, storageSaved, false);

        // Async similarity detection
        aiSimilarityService.analyzeSimilarityAsync(fileUUID, userId);

        // Build result
        DeduplicationResult result = new DeduplicationResult();
        result.setFileUUID(fileUUID);
        result.setOriginalFileName(fileName);
        result.setContentType(contentType);
        result.setOriginalSize(originalSize);
        result.setActualStorageUsed(actualStorageUsed);
        result.setStorageSaved(storageSaved);
        result.setDeduplicationRatio(deduplicationRatio);
        result.setExactDuplicate(false);
        result.setUniqueChunks(storedChunks);
        result.setDuplicateChunks(duplicateChunks);
        result.setTotalChunks(rabinChunks.size());
        result.setStatus("DEDUPLICATED");

        log.info("File deduplicated: {} - saved {} bytes ({:.2f}%)",
                fileName, storageSaved, deduplicationRatio * 100);

        return result;
    }

    private void updateUserStats(String userId, long originalSize, long actualUsed, long saved, boolean isExactDuplicate) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setTotalStorageUsed(user.getTotalStorageUsed() + originalSize);
            user.setActualStorageUsed(user.getActualStorageUsed() + actualUsed);
            user.setDeduplicatedStorageSaved(user.getDeduplicatedStorageSaved() + saved);
            user.setTotalFilesUploaded(user.getTotalFilesUploaded() + 1);
            if (isExactDuplicate || saved > 0) {
                user.setTotalDuplicatesDetected(user.getTotalDuplicatesDetected() + 1);
            }
            userRepository.save(user);
        });
    }

    /**
     * Get deduplication statistics for a user.
     *
     * @param userId the user ID
     * @return statistics
     */
    public DeduplicationStats getStats(String userId) {
        List<DeduplicatedFile> files = deduplicatedFileRepository.findByOwnerUserId(userId);
        List<FileChunk> chunks = fileChunkRepository.findByOwnerUserId(userId);

        long totalOriginalSize = files.stream().mapToLong(DeduplicatedFile::getOriginalSize).sum();
        long totalActualSize = files.stream().mapToLong(DeduplicatedFile::getActualStorageUsed).sum();
        long totalSaved = files.stream().mapToLong(DeduplicatedFile::getStorageSaved).sum();
        long totalChunksSize = chunks.stream().mapToLong(c -> c.getSize() != null ? c.getSize() : 0).sum();

        double overallRatio = totalOriginalSize > 0 ? (double) totalSaved / totalOriginalSize : 0.0;

        BloomFilterUtil.BloomFilterStats bloomStats = bloomFilterUtil.getUserFilterStats(userId);

        return new DeduplicationStats(
                files.size(),
                chunks.size(),
                totalOriginalSize,
                totalActualSize,
                totalSaved,
                overallRatio,
                chunks.stream().mapToLong(c -> c.getReferenceCount() != null ? c.getReferenceCount() : 0).sum(),
                bloomStats
        );
    }

    /**
     * Rebuild a file from its chunks.
     *
     * @param fileUUID the file UUID
     * @return the reconstructed file data
     */
    public byte[] reconstructFile(String fileUUID) {
        Optional<DeduplicatedFile> fileOpt = deduplicatedFileRepository.findByFileUUID(fileUUID);
        if (fileOpt.isEmpty()) {
            throw new IllegalArgumentException("File not found: " + fileUUID);
        }

        DeduplicatedFile file = fileOpt.get();
        
        // If it's an exact duplicate, reconstruct from original
        if (file.isExactDuplicate() && file.getOriginalFileId() != null) {
            return reconstructFile(file.getOriginalFileId());
        }

        // Collect all chunks and sort by sequence number
        Map<Integer, byte[]> chunkData = new HashMap<>();
        for (ChunkReference ref : file.getChunkReferences()) {
            if (ref.getChunkId() != null) {
                Optional<FileChunk> chunk = fileChunkRepository.findById(ref.getChunkId());
                chunk.ifPresent(c -> chunkData.put(ref.getSequenceNumber(), c.getData()));
            }
        }

        // Reconstruct file
        int totalSize = chunkData.values().stream().mapToInt(b -> b.length).sum();
        byte[] result = new byte[totalSize];
        int offset = 0;
        for (int i = 0; i < chunkData.size(); i++) {
            byte[] chunk = chunkData.get(i);
            if (chunk != null) {
                System.arraycopy(chunk, 0, result, offset, chunk.length);
                offset += chunk.length;
            }
        }

        return result;
    }

    /**
     * Delete a file and update chunk references.
     *
     * @param fileUUID the file UUID
     * @param userId   the user ID
     */
    public void deleteFile(String fileUUID, String userId) {
        Optional<DeduplicatedFile> fileOpt = deduplicatedFileRepository.findByFileUUID(fileUUID);
        if (fileOpt.isEmpty()) {
            throw new IllegalArgumentException("File not found: " + fileUUID);
        }

        DeduplicatedFile file = fileOpt.get();
        if (!file.getOwnerUserId().equals(userId)) {
            throw new IllegalArgumentException("Unauthorized access to file");
        }

        // Update chunk reference counts
        for (ChunkReference ref : file.getChunkReferences()) {
            if (ref.getChunkId() != null) {
                fileChunkRepository.findById(ref.getChunkId()).ifPresent(chunk -> {
                    chunk.setReferenceCount(chunk.getReferenceCount() - 1);
                    if (chunk.getReferenceCount() <= 0) {
                        fileChunkRepository.deleteById(chunk.getId());
                    } else {
                        fileChunkRepository.save(chunk);
                    }
                });
            }
        }

        // Delete file record
        deduplicatedFileRepository.deleteById(file.getId());

        log.info("File deleted: {}", fileUUID);
    }

    /**
     * Deduplication result DTO.
     */
    @lombok.Data
    public static class DeduplicationResult {
        private String fileUUID;
        private String originalFileName;
        private String contentType;
        private long originalSize;
        private long actualStorageUsed;
        private long storageSaved;
        private double deduplicationRatio;
        private boolean exactDuplicate;
        private String originalFileId;
        private int uniqueChunks;
        private int duplicateChunks;
        private int totalChunks;
        private String status;
    }

    /**
     * Deduplication statistics DTO.
     */
    public record DeduplicationStats(
            int totalFiles,
            int totalChunks,
            long totalOriginalSize,
            long totalActualSize,
            long totalSaved,
            double overallDeduplicationRatio,
            long totalChunkReferences,
            BloomFilterUtil.BloomFilterStats bloomFilterStats
    ) {}
}
