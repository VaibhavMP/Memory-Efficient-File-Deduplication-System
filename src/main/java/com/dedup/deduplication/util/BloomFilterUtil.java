package com.dedup.deduplication.util;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Bloom Filter implementation for fast duplicate detection.
 * 
 * Uses Guava's BloomFilter for memory-efficient probabilistic duplicate checking.
 * Provides O(1) lookup time for potential duplicates, with configurable
 * false positive rate.
 */
@Component
public class BloomFilterUtil {

    // Expected number of insertions
    private static final long EXPECTED_INSERTIONS = 1_000_000;
    
    // Desired false positive probability (0.01 = 1%)
    private static final double FALSE_POSITIVE_RATE = 0.01;

    // Local bloom filter cache for fast access
    private final ConcurrentHashMap<String, BloomFilter<String>> userBloomFilters;
    private final ConcurrentHashMap<String, ReadWriteLock> filterLocks;

    // Global bloom filter for system-wide duplicate detection
    private BloomFilter<String> globalBloomFilter;

    public BloomFilterUtil() {
        this.userBloomFilters = new ConcurrentHashMap<>();
        this.filterLocks = new ConcurrentHashMap<>();
        initializeGlobalBloomFilter();
    }

    private void initializeGlobalBloomFilter() {
        this.globalBloomFilter = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                EXPECTED_INSERTIONS,
                FALSE_POSITIVE_RATE
        );
    }

    /**
     * Gets or creates a bloom filter for a specific user.
     *
     * @param userId the user ID
     * @return the bloom filter for the user
     */
    public BloomFilter<String> getUserBloomFilter(String userId) {
        return userBloomFilters.computeIfAbsent(userId, k -> {
            filterLocks.put(k, new ReentrantReadWriteLock());
            return BloomFilter.create(
                    Funnels.stringFunnel(StandardCharsets.UTF_8),
                    EXPECTED_INSERTIONS / 10, // Smaller for individual users
                    FALSE_POSITIVE_RATE
            );
        });
    }

    /**
     * Gets the lock for a user's bloom filter.
     *
     * @param userId the user ID
     * @return the read-write lock
     */
    public ReadWriteLock getFilterLock(String userId) {
        return filterLocks.computeIfAbsent(userId, k -> new ReentrantReadWriteLock());
    }

    /**
     * Adds a hash to the user's bloom filter.
     *
     * @param userId the user ID
     * @param hash   the hash to add
     */
    public void addToUserFilter(String userId, String hash) {
        BloomFilter<String> filter = getUserBloomFilter(userId);
        ReadWriteLock lock = getFilterLock(userId);
        lock.writeLock().lock();
        try {
            filter.put(hash);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Adds a hash to the global bloom filter.
     *
     * @param hash the hash to add
     */
    public void addToGlobalFilter(String hash) {
        globalBloomFilter.put(hash);
    }

    /**
     * Checks if a hash might exist in the user's bloom filter.
     * May return false positives.
     *
     * @param userId the user ID
     * @param hash   the hash to check
     * @return true if the hash might exist, false if definitely not
     */
    public boolean mightExistInUserFilter(String userId, String hash) {
        BloomFilter<String> filter = userBloomFilters.get(userId);
        if (filter == null) {
            return false;
        }
        ReadWriteLock lock = getFilterLock(userId);
        lock.readLock().lock();
        try {
            return filter.mightContain(hash);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Checks if a hash might exist in the global bloom filter.
     * May return false positives.
     *
     * @param hash the hash to check
     * @return true if the hash might exist, false if definitely not
     */
    public boolean mightExistInGlobalFilter(String hash) {
        return globalBloomFilter.mightContain(hash);
    }

    /**
     * Checks if a hash might exist in either user or global filter.
     * For initial duplicate screening.
     *
     * @param userId the user ID
     * @param hash   the hash to check
     * @return true if the hash might exist in either filter
     */
    public boolean mightExistAnywhere(String userId, String hash) {
        return mightExistInGlobalFilter(hash) || mightExistInUserFilter(userId, hash);
    }

    /**
     * Adds multiple hashes to the user's bloom filter.
     *
     * @param userId the user ID
     * @param hashes the hashes to add
     */
    public void addAllToUserFilter(String userId, Iterable<String> hashes) {
        ReadWriteLock lock = getFilterLock(userId);
        lock.writeLock().lock();
        try {
            BloomFilter<String> filter = getUserBloomFilter(userId);
            for (String hash : hashes) {
                filter.put(hash);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Estimates the current size of the global bloom filter.
     *
     * @return estimated number of elements
     */
    public long estimateGlobalSize() {
        return globalBloomFilter.approximateElementCount();
    }

    /**
     * Estimates the current size of a user's bloom filter.
     *
     * @param userId the user ID
     * @return estimated number of elements
     */
    public long estimateUserFilterSize(String userId) {
        BloomFilter<String> filter = userBloomFilters.get(userId);
        return filter != null ? filter.approximateElementCount() : 0;
    }

    /**
     * Serializes a user's bloom filter to a byte array.
     *
     * @param userId the user ID
     * @return serialized bytes
     */
    public byte[] serializeUserFilter(String userId) {
        BloomFilter<String> filter = userBloomFilters.get(userId);
        if (filter == null) {
            return new byte[0];
        }
        ReadWriteLock lock = getFilterLock(userId);
        lock.readLock().lock();
        try {
            return filter.getBytes().toByteArray();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Serializes a user's bloom filter to an output stream.
     *
     * @param userId     the user ID
     * @param outputStream the output stream
     */
    public void serializeUserFilterToStream(String userId, OutputStream outputStream) throws IOException {
        BloomFilter<String> filter = userBloomFilters.get(userId);
        if (filter != null) {
            ReadWriteLock lock = getFilterLock(userId);
            lock.readLock().lock();
            try {
                filter.writeTo(outputStream);
            } finally {
                lock.readLock().unlock();
            }
        }
    }

    /**
     * Deserializes a bloom filter from a byte array.
     *
     * @param userId the user ID
     * @param data   the serialized data
     */
    public void deserializeUserFilter(String userId, byte[] data) {
        if (data == null || data.length == 0) {
            return;
        }
        ReadWriteLock lock = getFilterLock(userId);
        lock.writeLock().lock();
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
            BloomFilter<String> filter = BloomFilter.readFrom(inputStream, Funnels.stringFunnel(StandardCharsets.UTF_8));
            userBloomFilters.put(userId, filter);
        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialize bloom filter", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Clears a user's bloom filter.
     *
     * @param userId the user ID
     */
    public void clearUserFilter(String userId) {
        ReadWriteLock lock = getFilterLock(userId);
        lock.writeLock().lock();
        try {
            userBloomFilters.remove(userId);
            filterLocks.remove(userId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Clears the global bloom filter.
     */
    public void clearGlobalFilter() {
        initializeGlobalBloomFilter();
    }

    /**
     * Gets the expected false positive probability.
     *
     * @return the false positive rate
     */
    public double getFalsePositiveRate() {
        return FALSE_POSITIVE_RATE;
    }

    /**
     * Returns expected bits per element.
     *
     * @return bits per element
     */
    public double getExpectedBitsPerElement() {
        return BloomFilter.approach(expectedBits(), EXPECTED_INSERTIONS);
    }

    private static double expectedBits() {
        return -Math.log(FALSE_POSITIVE_RATE) / Math.log(2) * Math.log(2);
    }

    private static double approach(double bits, long insertions) {
        return bits * insertions / (bits + insertions);
    }

    /**
     * Statistics about a bloom filter.
     */
    public record BloomFilterStats(
            long expectedElements,
            double falsePositiveRate,
            long estimatedSize,
            double bitsPerElement
    ) {}

    /**
     * Gets statistics for a user's bloom filter.
     *
     * @param userId the user ID
     * @return statistics
     */
    public BloomFilterStats getUserFilterStats(String userId) {
        BloomFilter<String> filter = userBloomFilters.get(userId);
        return new BloomFilterStats(
                EXPECTED_INSERTIONS / 10,
                FALSE_POSITIVE_RATE,
                filter != null ? filter.approximateElementCount() : 0,
                getExpectedBitsPerElement()
        );
    }

    /**
     * Gets statistics for the global bloom filter.
     *
     * @return statistics
     */
    public BloomFilterStats getGlobalFilterStats() {
        return new BloomFilterStats(
                EXPECTED_INSERTIONS,
                FALSE_POSITIVE_RATE,
                globalBloomFilter.approximateElementCount(),
                getExpectedBitsPerElement()
        );
    }
}
