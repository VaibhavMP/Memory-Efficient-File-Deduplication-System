package com.dedup.deduplication.util;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Rabin Fingerprinting implementation for content-defined chunking.
 * 
 * This algorithm identifies chunk boundaries based on content characteristics
 * rather than fixed positions, making it ideal for deduplication as it can
 * detect duplicate chunks even when they are at different positions in files.
 */
@Component
public class RabinFingerprinting {

    // Default parameters
    private static final int DEFAULT_MIN_CHUNK_SIZE = 1024;  // 1 KB
    private static final int DEFAULT_MAX_CHUNK_SIZE = 8192; // 8 KB
    private static final int DEFAULT_AVG_CHUNK_SIZE = 4096; // 4 KB
    private static final int WINDOW_SIZE = 48;
    private static final int PRIME = 3; // Irreducible polynomial coefficient
    
    // Mask for finding chunk boundaries (when fingerprint & mask == 0)
    private static final long MASK_4KB = 0xFFF000L;
    private static final long MASK_2KB = 0x7FF000L;
    private static final long MASK_1KB = 0x3FF000L;

    private final int minChunkSize;
    private final int maxChunkSize;
    private final int avgChunkSize;
    private final long mask;

    public RabinFingerprinting() {
        this(DEFAULT_MIN_CHUNK_SIZE, DEFAULT_MAX_CHUNK_SIZE, DEFAULT_AVG_CHUNK_SIZE, MASK_4KB);
    }

    public RabinFingerprinting(int minChunkSize, int maxChunkSize, int avgChunkSize, long mask) {
        this.minChunkSize = minChunkSize;
        this.maxChunkSize = maxChunkSize;
        this.avgChunkSize = avgChunkSize;
        this.mask = mask;
    }

    /**
     * Represents a chunk identified by Rabin fingerprinting.
     */
    public record Chunk(int start, int end, long fingerprint, byte[] data) {
        public int length() {
            return end - start;
        }
    }

    /**
     * Chunks data using Rabin fingerprinting algorithm.
     *
     * @param data the input data to chunk
     * @return list of chunks
     */
    public List<Chunk> chunkData(byte[] data) {
        List<Chunk> chunks = new ArrayList<>();
        if (data == null || data.length == 0) {
            return chunks;
        }

        int dataLength = data.length;
        int chunkStart = 0;
        long fingerprint = 0;
        int position = 0;

        // Initialize the rolling hash with first WINDOW_SIZE bytes
        while (position < Math.min(WINDOW_SIZE, dataLength)) {
            fingerprint = updateFingerprint(fingerprint, data[position], position < WINDOW_SIZE - 1);
            position++;
        }

        while (position < dataLength) {
            // Add current byte to fingerprint
            fingerprint = updateFingerprint(fingerprint, data[position], false);

            int currentChunkSize = position - chunkStart + 1;

            // Check if we should create a chunk boundary
            boolean shouldChunk = false;

            // Always chunk at max size
            if (currentChunkSize >= maxChunkSize) {
                shouldChunk = true;
            }
            // Check fingerprint for boundary if we have minimum size
            else if (currentChunkSize >= minChunkSize) {
                // Create boundary when fingerprint matches mask (divisible by chunk size)
                if ((fingerprint & mask) == 0) {
                    shouldChunk = true;
                }
                // Also chunk if we've exceeded average size and fingerprint low bits are 0
                else if (currentChunkSize >= avgChunkSize && (fingerprint & 0xFFF) == 0) {
                    shouldChunk = true;
                }
            }

            if (shouldChunk) {
                // Create chunk from start to current position
                byte[] chunkData = new byte[currentChunkSize];
                System.arraycopy(data, chunkStart, chunkData, 0, currentChunkSize);
                chunks.add(new Chunk(chunkStart, position + 1, fingerprint, chunkData));
                chunkStart = position + 1;
                
                // Reset fingerprint for next chunk
                if (position + 1 < dataLength) {
                    fingerprint = 0;
                    int resetPos = position + 1;
                    int windowEnd = Math.min(resetPos + WINDOW_SIZE - 1, dataLength - 1);
                    for (int i = resetPos; i <= windowEnd; i++) {
                        fingerprint = updateFingerprint(fingerprint, data[i], i < windowEnd);
                    }
                }
            }

            position++;
        }

        // Add final chunk if there's remaining data
        if (chunkStart < dataLength) {
            int finalChunkSize = dataLength - chunkStart;
            byte[] chunkData = new byte[finalChunkSize];
            System.arraycopy(data, chunkStart, chunkData, 0, finalChunkSize);
            chunks.add(new Chunk(chunkStart, dataLength, fingerprint, chunkData));
        }

        return chunks;
    }

    /**
     * Updates the Rabin fingerprint using a rolling hash.
     * Uses the formula: new_fingerprint = (old_fingerprint - old_byte * power + new_byte) * prime mod m
     *
     * @param oldFingerprint the previous fingerprint
     * @param newByte       the new byte to add
     * @param isFirst       whether this is the first byte (no subtraction)
     * @return the updated fingerprint
     */
    private long updateFingerprint(long oldFingerprint, byte newByte, boolean isFirst) {
        // Simple polynomial rolling hash
        long newFingerprint;
        if (isFirst) {
            // For first byte, just add it with position weight
            newFingerprint = (newByte & 0xFF);
        } else {
            // Rolling update: multiply by prime and add new byte
            newFingerprint = ((oldFingerprint * PRIME) + (newByte & 0xFF)) & 0xFFFFFFFFFL;
        }
        return newFingerprint;
    }

    /**
     * Computes fingerprint for a specific byte array.
     *
     * @param data the data to compute fingerprint for
     * @return the fingerprint value
     */
    public long computeFingerprint(byte[] data) {
        if (data == null || data.length == 0) {
            return 0;
        }
        
        long fingerprint = 0;
        for (int i = 0; i < data.length; i++) {
            fingerprint = updateFingerprint(fingerprint, data[i], i == 0);
        }
        return fingerprint;
    }

    /**
     * Finds chunk boundaries in a stream efficiently.
     *
     * @param data the input data
     * @param callback callback for each chunk found
     */
    public void chunkDataStream(byte[] data, ChunkCallback callback) {
        if (data == null || data.length == 0) {
            return;
        }

        int dataLength = data.length;
        int chunkStart = 0;
        long fingerprint = 0;
        int position = 0;

        // Initialize fingerprint
        while (position < Math.min(WINDOW_SIZE, dataLength)) {
            fingerprint = updateFingerprint(fingerprint, data[position], position < WINDOW_SIZE - 1);
            position++;
        }

        while (position < dataLength) {
            fingerprint = updateFingerprint(fingerprint, data[position], false);
            int currentChunkSize = position - chunkStart + 1;

            boolean shouldChunk = false;
            if (currentChunkSize >= maxChunkSize) {
                shouldChunk = true;
            } else if (currentChunkSize >= minChunkSize && (fingerprint & mask) == 0) {
                shouldChunk = true;
            }

            if (shouldChunk) {
                byte[] chunkData = new byte[currentChunkSize];
                System.arraycopy(data, chunkStart, chunkData, 0, currentChunkSize);
                callback.onChunk(chunkStart, position + 1, fingerprint, chunkData);
                chunkStart = position + 1;
            }

            position++;
        }

        // Final chunk
        if (chunkStart < dataLength) {
            int finalChunkSize = dataLength - chunkStart;
            byte[] chunkData = new byte[finalChunkSize];
            System.arraycopy(data, chunkStart, chunkData, 0, finalChunkSize);
            callback.onChunk(chunkStart, dataLength, fingerprint, chunkData);
        }
    }

    /**
     * Callback interface for streaming chunk processing.
     */
    public interface ChunkCallback {
        void onChunk(int start, int end, long fingerprint, byte[] data);
    }

    /**
     * Gets statistics about the chunks produced.
     *
     * @param chunks the list of chunks
     * @return chunk statistics
     */
    public ChunkStatistics getStatistics(List<Chunk> chunks) {
        if (chunks.isEmpty()) {
            return new ChunkStatistics(0, 0, 0, 0, 0);
        }

        int totalSize = 0;
        int minSize = Integer.MAX_VALUE;
        int maxSize = 0;
        int count = chunks.size();

        for (Chunk chunk : chunks) {
            int size = chunk.length();
            totalSize += size;
            minSize = Math.min(minSize, size);
            maxSize = Math.max(maxSize, size);
        }

        return new ChunkStatistics(count, totalSize, totalSize / count, minSize, maxSize);
    }

    public record ChunkStatistics(int count, int totalSize, int avgSize, int minSize, int maxSize) {}
}
