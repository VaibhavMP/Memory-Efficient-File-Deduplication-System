package com.dedup.deduplication.util;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utility class for computing file hashes using MD5 and SHA-256 algorithms.
 */
@Component
public class HashingUtil {

    private static final int BUFFER_SIZE = 8192;

    /**
     * Computes SHA-256 hash of a byte array.
     *
     * @param data the input data
     * @return hexadecimal string of the SHA-256 hash
     */
    public String computeSHA256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Computes SHA-256 hash of a string.
     *
     * @param input the input string
     * @return hexadecimal string of the SHA-256 hash
     */
    public String computeSHA256(String input) {
        return computeSHA256(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Computes MD5 hash of a byte array.
     *
     * @param data the input data
     * @return hexadecimal string of the MD5 hash
     */
    public String computeMD5(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }

    /**
     * Computes MD5 hash of a string.
     *
     * @param input the input string
     * @return hexadecimal string of the MD5 hash
     */
    public String computeMD5(String input) {
        return computeMD5(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Computes SHA-256 hash of an input stream.
     * The stream is consumed but not closed.
     *
     * @param inputStream the input stream
     * @return hexadecimal string of the SHA-256 hash
     */
    public String computeSHA256(InputStream inputStream) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Computes MD5 hash of an input stream.
     * The stream is consumed but not closed.
     *
     * @param inputStream the input stream
     * @return hexadecimal string of the MD5 hash
     */
    public String computeMD5(InputStream inputStream) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }

    /**
     * Computes incremental SHA-256 hash using a MessageDigest instance.
     *
     * @param digest the MessageDigest instance
     * @param data   the data to update
     */
    public void updateSHA256(MessageDigest digest, byte[] data) {
        digest.update(data);
    }

    /**
     * Gets the final hash from a MessageDigest instance.
     *
     * @param digest the MessageDigest instance
     * @return hexadecimal string of the hash
     */
    public String getFinalHash(MessageDigest digest) {
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Creates a new SHA-256 MessageDigest instance.
     *
     * @return new MessageDigest instance
     */
    public MessageDigest createSHA256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Creates a new MD5 MessageDigest instance.
     *
     * @return new MessageDigest instance
     */
    public MessageDigest createMD5Digest() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }

    /**
     * Compares two hash strings in constant time to prevent timing attacks.
     *
     * @param hash1 first hash
     * @param hash2 second hash
     * @return true if hashes are equal
     */
    public boolean compareHashes(String hash1, String hash2) {
        if (hash1 == null || hash2 == null) {
            return false;
        }
        if (hash1.length() != hash2.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < hash1.length(); i++) {
            result |= hash1.charAt(i) ^ hash2.charAt(i);
        }
        return result == 0;
    }
}
