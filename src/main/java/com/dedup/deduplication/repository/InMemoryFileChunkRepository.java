package com.dedup.deduplication.repository;

import com.dedup.deduplication.model.FileChunk;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory repository for FileChunk entity.
 * Replaces MongoDB for standalone operation.
 */
@Repository
public class InMemoryFileChunkRepository {
    
    private final Map<String, FileChunk> chunks = new ConcurrentHashMap<>();
    private final Map<String, String> hashToId = new ConcurrentHashMap<>();

    public FileChunk save(FileChunk chunk) {
        if (chunk.getId() == null) {
            chunk.setId(UUID.randomUUID().toString());
        }
        chunks.put(chunk.getId(), chunk);
        if (chunk.getSha256Hash() != null) {
            hashToId.put(chunk.getSha256Hash(), chunk.getId());
        }
        return chunk;
    }

    public List<FileChunk> saveAll(List<FileChunk> chunkList) {
        for (FileChunk chunk : chunkList) {
            save(chunk);
        }
        return chunkList;
    }

    public Optional<FileChunk> findById(String id) {
        return Optional.ofNullable(chunks.get(id));
    }

    public Optional<FileChunk> findBySha256Hash(String sha256Hash) {
        String id = hashToId.get(sha256Hash);
        if (id != null) {
            return Optional.ofNullable(chunks.get(id));
        }
        return Optional.empty();
    }

    public List<FileChunk> findByOwnerUserId(String ownerUserId) {
        return chunks.values().stream()
                .filter(c -> ownerUserId.equals(c.getOwnerUserId()))
                .collect(Collectors.toList());
    }

    public List<FileChunk> findByContentType(String contentType) {
        return chunks.values().stream()
                .filter(c -> contentType.equals(c.getContentType()))
                .collect(Collectors.toList());
    }

    public List<FileChunk> findByChunkType(String chunkType) {
        return chunks.values().stream()
                .filter(c -> chunkType.equals(c.getChunkType()))
                .collect(Collectors.toList());
    }

    public boolean existsBySha256Hash(String sha256Hash) {
        return hashToId.containsKey(sha256Hash);
    }

    public long countByOwnerUserId(String ownerUserId) {
        return chunks.values().stream()
                .filter(c -> ownerUserId.equals(c.getOwnerUserId()))
                .count();
    }

    public List<FileChunk> findTop100ByOrderByReferenceCountDesc() {
        return chunks.values().stream()
                .sorted((a, b) -> Integer.compare(
                        b.getReferenceCount() != null ? b.getReferenceCount() : 0,
                        a.getReferenceCount() != null ? a.getReferenceCount() : 0
                ))
                .limit(100)
                .collect(Collectors.toList());
    }

    public void deleteById(String id) {
        FileChunk chunk = chunks.get(id);
        if (chunk != null && chunk.getSha256Hash() != null) {
            hashToId.remove(chunk.getSha256Hash());
        }
        chunks.remove(id);
    }

    public void deleteByOwnerUserId(String ownerUserId) {
        List<String> toDelete = chunks.values().stream()
                .filter(c -> ownerUserId.equals(c.getOwnerUserId()))
                .map(FileChunk::getId)
                .collect(Collectors.toList());
        toDelete.forEach(this::deleteById);
    }

    public List<FileChunk> findAll() {
        return new ArrayList<>(chunks.values());
    }

    public void deleteAll() {
        chunks.clear();
        hashToId.clear();
    }
}
