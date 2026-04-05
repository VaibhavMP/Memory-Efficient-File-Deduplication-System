package com.dedup.deduplication.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * User entity for multi-user support.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String username;

    @Indexed(unique = true)
    private String email;

    private String password;

    private String fullName;

    @Builder.Default
    private List<String> roles = new ArrayList<>();

    @Builder.Default
    private Long totalStorageUsed = 0L;

    @Builder.Default
    private Long actualStorageUsed = 0L;

    @Builder.Default
    private Long deduplicatedStorageSaved = 0L;

    @Builder.Default
    private Integer totalFilesUploaded = 0;

    @Builder.Default
    private Integer totalDuplicatesDetected = 0;

    @Builder.Default
    private boolean cloudSyncEnabled = false;

    private String cloudSyncStatus;

    private String lastSyncTimestamp;

    @Builder.Default
    private boolean active = true;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
