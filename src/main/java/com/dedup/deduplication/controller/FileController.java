package com.dedup.deduplication.controller;

import com.dedup.deduplication.model.DeduplicatedFile;
import com.dedup.deduplication.repository.InMemoryDeduplicatedFileRepository;
import com.dedup.deduplication.security.JwtAuthenticationFilter.UserPrincipal;
import com.dedup.deduplication.service.AISimilarityService;
import com.dedup.deduplication.service.CloudSyncService;
import com.dedup.deduplication.service.DeduplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for file operations.
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Slf4j
public class FileController {

    private final DeduplicationService deduplicationService;
    private final InMemoryDeduplicatedFileRepository deduplicatedFileRepository;
    private final CloudSyncService cloudSyncService;
    private final AISimilarityService aiSimilarityService;

    /**
     * Upload and deduplicate a file.
     */
    @PostMapping("/upload")
    public ResponseEntity<DeduplicationService.DeduplicationResult> uploadFile(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        try {
            DeduplicationService.DeduplicationResult result = deduplicationService.processFile(file, principal.userId());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error uploading file", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get all files for the current user.
     */
    @GetMapping
    public ResponseEntity<List<FileInfo>> getAllFiles(@AuthenticationPrincipal UserPrincipal principal) {
        List<DeduplicatedFile> files = deduplicatedFileRepository.findByOwnerUserId(principal.userId());
        
        List<FileInfo> fileInfos = files.stream()
                .map(this::toFileInfo)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(fileInfos);
    }

    /**
     * Get a specific file by UUID.
     */
    @GetMapping("/{fileUUID}")
    public ResponseEntity<FileInfo> getFile(
            @PathVariable String fileUUID,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        return deduplicatedFileRepository.findByFileUUID(fileUUID)
                .filter(f -> f.getOwnerUserId().equals(principal.userId()))
                .map(this::toFileInfo)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Download/reconstruct a file.
     */
    @GetMapping("/{fileUUID}/download")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String fileUUID,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        return deduplicatedFileRepository.findByFileUUID(fileUUID)
                .filter(f -> f.getOwnerUserId().equals(principal.userId()))
                .map(file -> {
                    try {
                        byte[] data = deduplicationService.reconstructFile(fileUUID);
                        ByteArrayResource resource = new ByteArrayResource(data);
                        
                        return ResponseEntity.ok()
                                .header(HttpHeaders.CONTENT_DISPOSITION,
                                        "attachment; filename=\"" + file.getOriginalFileName() + "\"")
                                .contentType(MediaType.parseMediaType(file.getContentType() != null ? 
                                        file.getContentType() : "application/octet-stream"))
                                .contentLength(data.length)
                                .body(resource);
                    } catch (Exception e) {
                        log.error("Error reconstructing file", e);
                        return ResponseEntity.internalServerError().<Resource>build();
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete a file.
     */
    @DeleteMapping("/{fileUUID}")
    public ResponseEntity<Void> deleteFile(
            @PathVariable String fileUUID,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        try {
            deduplicationService.deleteFile(fileUUID, principal.userId());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get deduplication statistics.
     */
    @GetMapping("/stats")
    public ResponseEntity<StatsResponse> getStats(@AuthenticationPrincipal UserPrincipal principal) {
        DeduplicationService.DeduplicationStats stats = deduplicationService.getStats(principal.userId());
        
        return ResponseEntity.ok(new StatsResponse(
                stats.totalFiles(),
                stats.totalChunks(),
                stats.totalOriginalSize(),
                stats.totalActualSize(),
                stats.totalSaved(),
                stats.overallDeduplicationRatio() * 100,
                stats.totalChunkReferences()
        ));
    }

    /**
     * Get similar files for a specific file.
     */
    @GetMapping("/{fileUUID}/similar")
    public ResponseEntity<List<AISimilarityService.SimilarFileResult>> getSimilarFiles(
            @PathVariable String fileUUID,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        return deduplicatedFileRepository.findByFileUUID(fileUUID)
                .filter(f -> f.getOwnerUserId().equals(principal.userId()))
                .map(file -> {
                    List<AISimilarityService.SimilarFileResult> similar = 
                            aiSimilarityService.analyzeSimilarity(fileUUID, principal.userId());
                    return ResponseEntity.ok(similar);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Trigger similarity analysis for a file.
     */
    @PostMapping("/{fileUUID}/analyze")
    public ResponseEntity<Void> analyzeFile(
            @PathVariable String fileUUID,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        return deduplicatedFileRepository.findByFileUUID(fileUUID)
                .filter(f -> f.getOwnerUserId().equals(principal.userId()))
                .map(file -> {
                    aiSimilarityService.analyzeSimilarityAsync(fileUUID, principal.userId());
                    return ResponseEntity.accepted().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * File info DTO.
     */
    private FileInfo toFileInfo(DeduplicatedFile file) {
        return new FileInfo(
                file.getId(),
                file.getFileUUID(),
                file.getOriginalFileName(),
                file.getContentType(),
                file.getOriginalSize(),
                file.getActualStorageUsed(),
                file.getStorageSaved(),
                file.getDeduplicationRatio() * 100,
                file.isExactDuplicate(),
                file.getUniqueChunks(),
                file.getDuplicateChunks(),
                file.getStatus(),
                file.getCloudSyncStatus(),
                file.getSimilarFiles() != null ? file.getSimilarFiles().size() : 0,
                file.getCreatedAt(),
                file.getUpdatedAt()
        );
    }

    /**
     * File info record.
     */
    public record FileInfo(
            String id,
            String fileUUID,
            String originalFileName,
            String contentType,
            long originalSize,
            long actualStorageUsed,
            long storageSaved,
            double deduplicationRatioPercent,
            boolean exactDuplicate,
            Integer uniqueChunks,
            Integer duplicateChunks,
            String status,
            String cloudSyncStatus,
            int similarFilesCount,
            java.time.LocalDateTime createdAt,
            java.time.LocalDateTime updatedAt
    ) {}

    /**
     * Stats response record.
     */
    public record StatsResponse(
            int totalFiles,
            int totalChunks,
            long totalOriginalSize,
            long totalActualSize,
            long totalSaved,
            double savedPercentage,
            long totalChunkReferences
    ) {}
}
