package com.dedup.deduplication.service;

import com.dedup.deduplication.model.DeduplicatedFile;
import com.dedup.deduplication.model.DeduplicatedFile.SimilarFile;
import com.dedup.deduplication.repository.InMemoryDeduplicatedFileRepository;
import com.dedup.deduplication.util.HashingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * AI-based similarity detection service.
 * 
 * Uses content analysis, structure comparison, and pattern matching
 * to detect similar files even when they are not exact duplicates.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AISimilarityService {

    private final InMemoryDeduplicatedFileRepository deduplicatedFileRepository;
    private final HashingUtil hashingUtil;
    private final Tika tika;

    // Similarity thresholds
    private static final double HIGH_SIMILARITY_THRESHOLD = 0.8;
    private static final double MEDIUM_SIMILARITY_THRESHOLD = 0.5;
    private static final double LOW_SIMILARITY_THRESHOLD = 0.3;

    // Content analysis features
    private static final int NGRAM_SIZE = 3;
    private static final int MAX_FEATURES = 100;

    public AISimilarityService(InMemoryDeduplicatedFileRepository deduplicatedFileRepository, HashingUtil hashingUtil) {
        this.deduplicatedFileRepository = deduplicatedFileRepository;
        this.hashingUtil = hashingUtil;
        this.tika = new Tika();
    }

    /**
     * Asynchronously analyze file for similar files.
     *
     * @param fileUUID the file UUID
     * @param userId   the user ID
     */
    @Async
    public CompletableFuture<Void> analyzeSimilarityAsync(String fileUUID, String userId) {
        try {
            analyzeSimilarity(fileUUID, userId);
        } catch (Exception e) {
            log.error("Error analyzing similarity for file: {}", fileUUID, e);
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Analyze a file for similar files.
     *
     * @param fileUUID the file UUID
     * @param userId   the user ID
     * @return list of similar files
     */
    public List<SimilarFileResult> analyzeSimilarity(String fileUUID, String userId) {
        Optional<DeduplicatedFile> fileOpt = deduplicatedFileRepository.findByFileUUID(fileUUID);
        if (fileOpt.isEmpty()) {
            return Collections.emptyList();
        }

        DeduplicatedFile targetFile = fileOpt.get();
        byte[] targetData = reconstructFileData(targetFile);

        // Extract features from target file
        FileFeatures targetFeatures = extractFeatures(targetFile, targetData);

        // Get all other files for this user
        List<DeduplicatedFile> otherFiles = deduplicatedFileRepository.findByOwnerUserId(userId)
                .stream()
                .filter(f -> !f.getFileUUID().equals(fileUUID))
                .filter(f -> !f.isExactDuplicate())
                .collect(Collectors.toList());

        List<SimilarFileResult> similarFiles = new ArrayList<>();

        for (DeduplicatedFile otherFile : otherFiles) {
            double similarity = calculateOverallSimilarity(targetFile, otherFile, targetData, targetFeatures);
            
            if (similarity >= LOW_SIMILARITY_THRESHOLD) {
                SimilarFileResult result = new SimilarFileResult(
                        otherFile.getFileUUID(),
                        otherFile.getOriginalFileName(),
                        similarity,
                        determineSimilarityType(similarity),
                        otherFile.getOriginalSize(),
                        otherFile.getDeduplicationRatio()
                );
                similarFiles.add(result);

                // Update the similar files list in the database
                updateSimilarFiles(targetFile, otherFile, similarity);
            }
        }

        // Sort by similarity score descending
        similarFiles.sort((a, b) -> Double.compare(b.similarityScore(), a.similarityScore()));

        log.info("Found {} similar files for: {}", similarFiles.size(), fileUUID);
        return similarFiles;
    }

    /**
     * Extract features from a file for similarity comparison.
     */
    private FileFeatures extractFeatures(DeduplicatedFile file, byte[] data) {
        FileFeatures features = new FileFeatures();

        // Content type based features
        features.setContentType(file.getContentType());

        // File extension based features
        String extension = getFileExtension(file.getOriginalFileName());
        features.setExtension(extension);

        // Size based features
        features.setSize(file.getOriginalSize());

        // Content-based features for text files
        if (isTextContent(file.getContentType())) {
            extractTextFeatures(data, features);
        }

        // Structural features
        extractStructuralFeatures(file, features);

        // Chunk-based features (for binary files)
        if (file.getChunkReferences() != null) {
            features.setChunkCount(file.getChunkReferences().size());
            features.setUniqueChunks(file.getUniqueChunks());
            features.setChunkRatio(file.getUniqueChunks() != null && file.getChunkReferences().size() > 0
                    ? (double) file.getUniqueChunks() / file.getChunkReferences().size()
                    : 0.0);
        }

        // N-gram features
        if (isTextContent(file.getContentType())) {
            features.setNgrams(extractNgrams(data, NGRAM_SIZE));
        }

        return features;
    }

    private void extractTextFeatures(byte[] data, FileFeatures features) {
        try {
            String text = new String(data);
            
            // Word count
            features.setWordCount(text.split("\\s+").length);
            
            // Line count
            features.setLineCount(text.split("\n").length);
            
            // Average word length
            String[] words = text.split("\\s+");
            if (words.length > 0) {
                double avgWordLength = Arrays.stream(words)
                        .filter(w -> !w.isEmpty())
                        .mapToInt(String::length)
                        .average()
                        .orElse(0.0);
                features.setAvgWordLength(avgWordLength);
            }

            // Character frequency
            Map<Character, Integer> charFreq = new HashMap<>();
            for (char c : text.toCharArray()) {
                if (Character.isLetterOrDigit(c)) {
                    charFreq.merge(Character.toLowerCase(c), 1, Integer::sum);
                }
            }
            features.setCharacterFrequency(charFreq);

        } catch (Exception e) {
            log.warn("Error extracting text features", e);
        }
    }

    private void extractStructuralFeatures(DeduplicatedFile file, FileFeatures features) {
        // Hash distribution features based on chunk hashes
        if (file.getChunkReferences() != null) {
            List<String> hashes = file.getChunkReferences().stream()
                    .map(r -> r.getSha256Hash())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            features.setHashes(hashes);

            // Calculate hash prefix distribution
            Map<String, Long> prefixDistribution = hashes.stream()
                    .collect(Collectors.groupingBy(
                            h -> h.length() > 2 ? h.substring(0, 2) : h,
                            Collectors.counting()
                    ));
            features.setHashPrefixDistribution(prefixDistribution);
        }
    }

    private Set<String> extractNgrams(byte[] data, int n) {
        String text = new String(data).toLowerCase();
        Set<String> ngrams = new HashSet<>();
        
        String[] words = text.split("\\s+");
        for (int i = 0; i <= words.length - n; i++) {
            ngrams.add(String.join(" ", Arrays.copyOfRange(words, i, i + n)));
        }
        
        // Limit n-gram count
        if (ngrams.size() > MAX_FEATURES) {
            return new HashSet<>(ngrams.stream().limit(MAX_FEATURES).collect(Collectors.toList()));
        }
        return ngrams;
    }

    /**
     * Calculate overall similarity between two files.
     */
    private double calculateOverallSimilarity(DeduplicatedFile target, DeduplicatedFile other,
                                               byte[] targetData, FileFeatures targetFeatures) {
        List<Double> similarities = new ArrayList<>();

        // Content type similarity
        double contentTypeSim = calculateContentTypeSimilarity(target.getContentType(), other.getContentType());
        similarities.add(contentTypeSim * 0.15);

        // Extension similarity
        double extensionSim = calculateExtensionSimilarity(
                getFileExtension(target.getOriginalFileName()),
                getFileExtension(other.getOriginalFileName())
        );
        similarities.add(extensionSim * 0.15);

        // Size similarity
        double sizeSim = calculateSizeSimilarity(target.getOriginalSize(), other.getOriginalSize());
        similarities.add(sizeSim * 0.1);

        // Chunk overlap similarity
        double chunkSim = calculateChunkSimilarity(target, other);
        similarities.add(chunkSim * 0.25);

        // Text content similarity (if applicable)
        if (isTextContent(target.getContentType()) && isTextContent(other.getContentType())) {
            byte[] otherData = reconstructFileData(other);
            double textSim = calculateTextSimilarity(targetData, otherData);
            similarities.add(textSim * 0.2);
        }

        // N-gram similarity
        if (targetFeatures.getNgrams() != null && !targetFeatures.getNgrams().isEmpty()) {
            FileFeatures otherFeatures = extractFeatures(other, reconstructFileData(other));
            if (otherFeatures.getNgrams() != null) {
                double ngramSim = calculateJaccardSimilarity(
                        targetFeatures.getNgrams(),
                        otherFeatures.getNgrams()
                );
                similarities.add(ngramSim * 0.15);
            }
        }

        return similarities.stream().mapToDouble(Double::doubleValue).sum();
    }

    private double calculateContentTypeSimilarity(String type1, String type2) {
        if (type1 == null || type2 == null) return 0.0;
        if (type1.equals(type2)) return 1.0;
        
        // Extract category (e.g., "image" from "image/png")
        String cat1 = type1.split("/")[0];
        String cat2 = type2.split("/")[0];
        return cat1.equals(cat2) ? 0.7 : 0.0;
    }

    private double calculateExtensionSimilarity(String ext1, String ext2) {
        if (ext1 == null || ext2 == null) return 0.0;
        return ext1.equalsIgnoreCase(ext2) ? 1.0 : 0.0;
    }

    private double calculateSizeSimilarity(long size1, long size2) {
        if (size1 == 0 || size2 == 0) return 0.0;
        double ratio = Math.min(size1, size2) / (double) Math.max(size1, size2);
        return ratio; // Returns 0.0 to 1.0
    }

    private double calculateChunkSimilarity(DeduplicatedFile file1, DeduplicatedFile file2) {
        if (file1.getChunkReferences() == null || file2.getChunkReferences() == null) {
            return 0.0;
        }

        Set<String> hashes1 = file1.getChunkReferences().stream()
                .map(r -> r.getSha256Hash())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> hashes2 = file2.getChunkReferences().stream()
                .map(r -> r.getSha256Hash())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (hashes1.isEmpty() || hashes2.isEmpty()) return 0.0;

        // Jaccard similarity
        Set<String> intersection = new HashSet<>(hashes1);
        intersection.retainAll(hashes2);

        Set<String> union = new HashSet<>(hashes1);
        union.addAll(hashes2);

        return (double) intersection.size() / union.size();
    }

    private double calculateTextSimilarity(byte[] data1, byte[] data2) {
        String text1 = new String(data1).toLowerCase();
        String text2 = new String(data2).toLowerCase();

        // Character-level similarity
        double charSim = calculateJaccardSimilarity(
                new HashSet<>(text1.chars().mapToObj(c -> (char) c).toList()),
                new HashSet<>(text2.chars().mapToObj(c -> (char) c).toList())
        );

        // Word-level similarity
        Set<String> words1 = new HashSet<>(Arrays.asList(text1.split("\\s+")));
        Set<String> words2 = new HashSet<>(Arrays.asList(text2.split("\\s+")));
        double wordSim = calculateJaccardSimilarity(words1, words2);

        return (charSim * 0.3 + wordSim * 0.7);
    }

    private double calculateJaccardSimilarity(Set<?> set1, Set<?> set2) {
        if (set1.isEmpty() && set2.isEmpty()) return 1.0;
        if (set1.isEmpty() || set2.isEmpty()) return 0.0;

        Set<?> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        Set<?> union = new HashSet<>(set1);
        union.addAll(set2);

        return (double) intersection.size() / union.size();
    }

    private String determineSimilarityType(double score) {
        if (score >= HIGH_SIMILARITY_THRESHOLD) return "HIGH";
        if (score >= MEDIUM_SIMILARITY_THRESHOLD) return "MEDIUM";
        if (score >= LOW_SIMILARITY_THRESHOLD) return "LOW";
        return "NONE";
    }

    private void updateSimilarFiles(DeduplicatedFile target, DeduplicatedFile other, double similarity) {
        DeduplicatedFile.SimilarFile similarFile = DeduplicatedFile.SimilarFile.builder()
                .fileId(other.getFileUUID())
                .fileName(other.getOriginalFileName())
                .similarityScore(similarity)
                .similarityType(determineSimilarityType(similarity))
                .build();

        List<DeduplicatedFile.SimilarFile> similarFiles = target.getSimilarFiles();
        if (similarFiles == null) {
            similarFiles = new ArrayList<>();
        }
        
        // Remove existing entry if present
        similarFiles.removeIf(sf -> sf.getFileId().equals(other.getFileUUID()));
        similarFiles.add(similarFile);
        
        target.setSimilarFiles(similarFiles);
        target.setSimilarityScore(similarity);
        
        deduplicatedFileRepository.save(target);
    }

    private byte[] reconstructFileData(DeduplicatedFile file) {
        // For similarity analysis, we return a minimal representation
        // In production, this would reconstruct from chunks
        return new byte[0];
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }

    private boolean isTextContent(String contentType) {
        if (contentType == null) return false;
        return contentType.startsWith("text/") ||
               contentType.contains("json") ||
               contentType.contains("xml") ||
               contentType.contains("javascript");
    }

    /**
     * File features container.
     */
    @lombok.Data
    private static class FileFeatures {
        private String contentType;
        private String extension;
        private long size;
        private int wordCount;
        private int lineCount;
        private double avgWordLength;
        private Map<Character, Integer> characterFrequency;
        private Set<String> ngrams;
        private int chunkCount;
        private Integer uniqueChunks;
        private Double chunkRatio;
        private List<String> hashes;
        private Map<String, Long> hashPrefixDistribution;
    }

    /**
     * Similar file result DTO.
     */
    public record SimilarFileResult(
            String fileId,
            String fileName,
            double similarityScore,
            String similarityType,
            long fileSize,
            double deduplicationRatio
    ) {}
}
