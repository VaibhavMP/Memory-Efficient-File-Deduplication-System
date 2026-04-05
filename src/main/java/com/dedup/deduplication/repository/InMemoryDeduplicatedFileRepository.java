package com.dedup.deduplication.repository;

import com.dedup.deduplication.model.DeduplicatedFile;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory repository for DeduplicatedFile entity.
 * Replaces MongoDB for standalone operation.
 */
@Repository
public class InMemoryDeduplicatedFileRepository {
    
    private final Map<String, DeduplicatedFile> files = new ConcurrentHashMap<>();
    private final Map<String, String> uuidToId = new ConcurrentHashMap<>();
    private final Map<String, List<String>> hashToFileIds = new ConcurrentHashMap<>();

    public DeduplicatedFile save(DeduplicatedFile file) {
        if (file.getId() == null) {
            file.setId(UUID.randomUUID().toString());
        }
        if (file.getFileUUID() == null) {
            file.setFileUUID(UUID.randomUUID().toString());
        }
        files.put(file.getId(), file);
        uuidToId.put(file.getFileUUID(), file.getId());
        
        if (file.getFullFileSha256Hash() != null) {
            hashToFileIds.computeIfAbsent(file.getFullFileSha256Hash(), k -> new ArrayList<>()).add(file.getId());
        }
        return file;
    }

    public Optional<DeduplicatedFile> findById(String id) {
        return Optional.ofNullable(files.get(id));
    }

    public Optional<DeduplicatedFile> findByFileUUID(String fileUUID) {
        String id = uuidToId.get(fileUUID);
        if (id != null) {
            return Optional.ofNullable(files.get(id));
        }
        return Optional.empty();
    }

    public List<DeduplicatedFile> findByOwnerUserId(String ownerUserId) {
        return files.values().stream()
                .filter(f -> ownerUserId.equals(f.getOwnerUserId()))
                .collect(Collectors.toList());
    }

    public List<DeduplicatedFile> findByFullFileSha256Hash(String fullFileSha256Hash) {
        List<String> ids = hashToFileIds.get(fullFileSha256Hash);
        if (ids != null) {
            return ids.stream()
                    .map(files::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    public List<DeduplicatedFile> findByOwnerUserIdAndStatus(String ownerUserId, String status) {
        return files.values().stream()
                .filter(f -> ownerUserId.equals(f.getOwnerUserId()))
                .filter(f -> status.equals(f.getStatus()))
                .collect(Collectors.toList());
    }

    public List<DeduplicatedFile> findByOwnerUserIdAndCloudSyncStatus(String ownerUserId, String cloudSyncStatus) {
        return files.values().stream()
                .filter(f -> ownerUserId.equals(f.getOwnerUserId()))
                .filter(f -> cloudSyncStatus.equals(f.getCloudSyncStatus()))
                .collect(Collectors.toList());
    }

    public boolean existsByFullFileSha256Hash(String fullFileSha256Hash) {
        return hashToFileIds.containsKey(fullFileSha256Hash);
    }

    public long countByOwnerUserId(String ownerUserId) {
        return files.values().stream()
                .filter(f -> ownerUserId.equals(f.getOwnerUserId()))
                .count();
    }

    public long countByFullFileSha256Hash(String fullFileSha256Hash) {
        List<String> ids = hashToFileIds.get(fullFileSha256Hash);
        return ids != null ? ids.size() : 0;
    }

    public List<DeduplicatedFile> findByDeduplicationRatioGreaterThan(Double minRatio) {
        return files.values().stream()
                .filter(f -> f.getDeduplicationRatio() != null && f.getDeduplicationRatio() > minRatio)
                .collect(Collectors.toList());
    }

    public List<DeduplicatedFile> findByIsExactDuplicateTrue() {
        return files.values().stream()
                .filter(DeduplicatedFile::isExactDuplicate)
                .collect(Collectors.toList());
    }

    public void deleteById(String id) {
        DeduplicatedFile file = files.get(id);
        if (file != null) {
            if (file.getFileUUID() != null) {
                uuidToId.remove(file.getFileUUID());
            }
            if (file.getFullFileSha256Hash() != null) {
                List<String> ids = hashToFileIds.get(file.getFullFileSha256Hash());
                if (ids != null) {
                    ids.remove(id);
                    if (ids.isEmpty()) {
                        hashToFileIds.remove(file.getFullFileSha256Hash());
                    }
                }
            }
        }
        files.remove(id);
    }

    public void deleteByOwnerUserId(String ownerUserId) {
        List<String> toDelete = files.values().stream()
                .filter(f -> ownerUserId.equals(f.getOwnerUserId()))
                .map(DeduplicatedFile::getId)
                .collect(Collectors.toList());
        toDelete.forEach(this::deleteById);
    }

    public List<DeduplicatedFile> findByCloudSyncStatus(String cloudSyncStatus) {
        return files.values().stream()
                .filter(f -> cloudSyncStatus.equals(f.getCloudSyncStatus()))
                .collect(Collectors.toList());
    }

    public List<DeduplicatedFile> findAll() {
        return new ArrayList<>(files.values());
    }

    public void deleteAll() {
        files.clear();
        uuidToId.clear();
        hashToFileIds.clear();
    }
}
