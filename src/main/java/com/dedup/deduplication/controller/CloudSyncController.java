package com.dedup.deduplication.controller;

import com.dedup.deduplication.security.JwtAuthenticationFilter.UserPrincipal;
import com.dedup.deduplication.service.CloudSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for cloud sync operations.
 */
@RestController
@RequestMapping("/api/cloud")
@RequiredArgsConstructor
public class CloudSyncController {

    private final CloudSyncService cloudSyncService;

    /**
     * Sync all pending files to cloud.
     */
    @PostMapping("/sync")
    public ResponseEntity<CloudSyncService.SyncResult> syncAll(
            @AuthenticationPrincipal UserPrincipal principal) {
        
        CloudSyncService.SyncResult result = cloudSyncService.syncAllPending(principal.userId());
        return ResponseEntity.ok(result);
    }

    /**
     * Sync a specific file to cloud.
     */
    @PostMapping("/sync/{fileUUID}")
    public ResponseEntity<CloudSyncService.CloudSyncResult> syncFile(
            @PathVariable String fileUUID,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        CloudSyncService.CloudSyncResult result = cloudSyncService.syncFile(fileUUID, principal.userId());
        return ResponseEntity.ok(result);
    }

    /**
     * Restore a file from cloud.
     */
    @PostMapping("/restore/{fileUUID}")
    public ResponseEntity<CloudSyncService.CloudSyncResult> restoreFile(
            @PathVariable String fileUUID,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        CloudSyncService.CloudSyncResult result = cloudSyncService.restoreFromCloud(fileUUID, principal.userId());
        return ResponseEntity.ok(result);
    }

    /**
     * Get sync status summary.
     */
    @GetMapping("/status")
    public ResponseEntity<CloudSyncService.SyncStatusSummary> getStatus(
            @AuthenticationPrincipal UserPrincipal principal) {
        
        CloudSyncService.SyncStatusSummary summary = cloudSyncService.getSyncStatus(principal.userId());
        return ResponseEntity.ok(summary);
    }

    /**
     * Get storage breakdown.
     */
    @GetMapping("/storage")
    public ResponseEntity<CloudSyncService.CloudStorageBreakdown> getStorageBreakdown(
            @AuthenticationPrincipal UserPrincipal principal) {
        
        CloudSyncService.CloudStorageBreakdown breakdown = cloudSyncService.getStorageBreakdown(principal.userId());
        return ResponseEntity.ok(breakdown);
    }
}
