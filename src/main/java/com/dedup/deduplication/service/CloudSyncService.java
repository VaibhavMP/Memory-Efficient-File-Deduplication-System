package com.dedup.deduplication.service;

import com.dedup.deduplication.model.DeduplicatedFile;
import com.dedup.deduplication.model.User;
import com.dedup.deduplication.repository.InMemoryDeduplicatedFileRepository;
import com.dedup.deduplication.repository.InMemoryUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cloud Sync simulation service.
 * Simulates syncing deduplicated files to a cloud storage.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CloudSyncService {

    private final InMemoryDeduplicatedFileRepository deduplicatedFileRepository;
    private final InMemoryUserRepository userRepository;
    private final DeduplicationService deduplicationService;

    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);
    private final Random random = new Random();

    // Simulated cloud storage parameters
    private static final double SYNC_SUCCESS_RATE = 0.95; // 95% success rate
    private static final long SYNC_DELAY_MS = 500; // Simulated network delay

    /**
     * Sync all pending files to cloud.
     *
     * @param userId the user ID
     * @return sync result
     */
    public SyncResult syncAllPending(String userId) {
        if (!syncInProgress.compareAndSet(false, true)) {
            return new SyncResult(false, "Sync already in progress", 0, 0, 0);
        }

        try {
            List<DeduplicatedFile> pendingFiles = deduplicatedFileRepository
                    .findByOwnerUserIdAndCloudSyncStatus(userId, "PENDING");

            int successCount = 0;
            int failCount = 0;
            long totalSyncedSize = 0;

            for (DeduplicatedFile file : pendingFiles) {
                if (simulateCloudSync(file)) {
                    file.setCloudSyncStatus("SYNCED");
                    file.setCloudSyncTimestamp(LocalDateTime.now());
                    deduplicatedFileRepository.save(file);
                    successCount++;
                    totalSyncedSize += file.getActualStorageUsed();
                } else {
                    file.setCloudSyncStatus("FAILED");
                    deduplicatedFileRepository.save(file);
                    failCount++;
                }
            }

            // Update user sync status
            userRepository.findById(userId).ifPresent(user -> {
                user.setCloudSyncStatus("SYNCED");
                user.setLastSyncTimestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                userRepository.save(user);
            });

            log.info("Cloud sync completed for user {}: {} success, {} failed",
                    userId, successCount, failCount);

            return new SyncResult(true, "Sync completed",
                    pendingFiles.size(), successCount, totalSyncedSize);

        } finally {
            syncInProgress.set(false);
        }
    }

    /**
     * Sync a specific file to cloud.
     *
     * @param fileUUID the file UUID
     * @param userId   the user ID
     * @return sync result
     */
    public CloudSyncResult syncFile(String fileUUID, String userId) {
        return deduplicatedFileRepository.findByFileUUID(fileUUID)
                .map(file -> {
                    if (!file.getOwnerUserId().equals(userId)) {
                        return new CloudSyncResult(false, "Unauthorized", fileUUID);
                    }

                    if (simulateCloudSync(file)) {
                        file.setCloudSyncStatus("SYNCED");
                        file.setCloudSyncTimestamp(LocalDateTime.now());
                        deduplicatedFileRepository.save(file);
                        return new CloudSyncResult(true, "File synced successfully", fileUUID);
                    } else {
                        file.setCloudSyncStatus("FAILED");
                        deduplicatedFileRepository.save(file);
                        return new CloudSyncResult(false, "Sync failed - network error", fileUUID);
                    }
                })
                .orElse(new CloudSyncResult(false, "File not found", fileUUID));
    }

    /**
     * Restore a file from cloud.
     *
     * @param fileUUID the file UUID
     * @param userId   the user ID
     * @return restore result
     */
    public CloudSyncResult restoreFromCloud(String fileUUID, String userId) {
        return deduplicatedFileRepository.findByFileUUID(fileUUID)
                .map(file -> {
                    if (!file.getOwnerUserId().equals(userId)) {
                        return new CloudSyncResult(false, "Unauthorized", fileUUID);
                    }

                    if (simulateCloudRestore(file)) {
                        file.setCloudSyncStatus("SYNCED");
                        deduplicatedFileRepository.save(file);
                        return new CloudSyncResult(true, "File restored successfully", fileUUID);
                    } else {
                        return new CloudSyncResult(false, "Restore failed", fileUUID);
                    }
                })
                .orElse(new CloudSyncResult(false, "File not found", fileUUID));
    }

    /**
     * Get cloud sync status for all files.
     *
     * @param userId the user ID
     * @return sync status summary
     */
    public SyncStatusSummary getSyncStatus(String userId) {
        List<DeduplicatedFile> files = deduplicatedFileRepository.findByOwnerUserId(userId);

        int pending = 0;
        int synced = 0;
        int failed = 0;
        long totalPendingSize = 0;
        long totalSyncedSize = 0;

        for (DeduplicatedFile file : files) {
            switch (file.getCloudSyncStatus()) {
                case "PENDING" -> {
                    pending++;
                    totalPendingSize += file.getActualStorageUsed();
                }
                case "SYNCED" -> {
                    synced++;
                    totalSyncedSize += file.getActualStorageUsed();
                }
                case "FAILED" -> failed++;
            }
        }

        return new SyncStatusSummary(pending, synced, failed, totalPendingSize, totalSyncedSize);
    }

    /**
     * Get storage breakdown for cloud vs local.
     *
     * @param userId the user ID
     * @return storage breakdown
     */
    public CloudStorageBreakdown getStorageBreakdown(String userId) {
        return userRepository.findById(userId)
                .map(user -> new CloudStorageBreakdown(
                        user.getTotalStorageUsed(),
                        user.getActualStorageUsed(),
                        user.getDeduplicatedStorageSaved(),
                        calculateCloudStorageEstimate(userId),
                        calculateLocalStorageEstimate(userId)
                ))
                .orElse(new CloudStorageBreakdown(0, 0, 0, 0, 0));
    }

    /**
     * Scheduled task to auto-sync pending files every 5 minutes.
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void autoSyncPendingFiles() {
        log.info("Running auto-sync for pending files");

        List<DeduplicatedFile> pendingFiles = deduplicatedFileRepository
                .findByCloudSyncStatus("PENDING");

        for (DeduplicatedFile file : pendingFiles) {
            try {
                if (simulateCloudSync(file)) {
                    file.setCloudSyncStatus("SYNCED");
                    file.setCloudSyncTimestamp(LocalDateTime.now());
                    deduplicatedFileRepository.save(file);
                }
            } catch (Exception e) {
                log.error("Error auto-syncing file: {}", file.getFileUUID(), e);
            }
        }
    }

    /**
     * Simulate cloud sync operation.
     */
    private boolean simulateCloudSync(DeduplicatedFile file) {
        try {
            // Simulate network delay
            Thread.sleep(SYNC_DELAY_MS + random.nextInt(500));

            // Simulate success/failure
            boolean success = random.nextDouble() < SYNC_SUCCESS_RATE;

            if (success) {
                log.debug("Simulated cloud sync for file: {}", file.getFileUUID());
            } else {
                log.warn("Simulated sync failure for file: {}", file.getFileUUID());
            }

            return success;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Simulate cloud restore operation.
     */
    private boolean simulateCloudRestore(DeduplicatedFile file) {
        try {
            Thread.sleep(SYNC_DELAY_MS + random.nextInt(300));
            return random.nextDouble() < SYNC_SUCCESS_RATE;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private long calculateCloudStorageEstimate(String userId) {
        List<DeduplicatedFile> files = deduplicatedFileRepository.findByOwnerUserId(userId);
        return files.stream()
                .filter(f -> "SYNCED".equals(f.getCloudSyncStatus()))
                .mapToLong(DeduplicatedFile::getActualStorageUsed)
                .sum();
    }

    private long calculateLocalStorageEstimate(String userId) {
        return deduplicationService.getStats(userId).totalActualSize();
    }

    /**
     * Sync result DTO.
     */
    public record SyncResult(
            boolean success,
            String message,
            int totalFiles,
            int syncedFiles,
            long syncedSize
    ) {}

    /**
     * Cloud sync result for single file.
     */
    public record CloudSyncResult(
            boolean success,
            String message,
            String fileUUID
    ) {}

    /**
     * Sync status summary.
     */
    public record SyncStatusSummary(
            int pendingCount,
            int syncedCount,
            int failedCount,
            long pendingSize,
            long syncedSize
    ) {}

    /**
     * Cloud storage breakdown.
     */
    public record CloudStorageBreakdown(
            long totalUploadedSize,
            long actualStorageSize,
            long savedByDeduplication,
            long cloudStorageUsed,
            long localStorageUsed
    ) {}
}
