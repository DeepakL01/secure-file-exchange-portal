package com.college.securefile.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utility for cryptographic hashing and constant-time comparisons.
 * Ensures access tokens and revocation tokens are only stored as SHA-256 hashes.
 */
public final class HashUtils {

    private HashUtils() {
        // Prevent instantiation
    }

    /**
     * Computes the SHA-256 hash of a plaintext token and returns a 64-character hex string.
     *
     * @param token the secret token
     * @return hex-encoded SHA-256 hash
     */
    public static String sha256Hex(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token to hash cannot be null or blank");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(token.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable in JVM", e);
        }
    }

    /**
     * Compares two hex hashes in constant time to prevent timing attacks.
     */
    public static boolean constantTimeEquals(String hashA, String hashB) {
        if (hashA == null || hashB == null) {
            return false;
        }
        byte[] a = hashA.getBytes(StandardCharsets.UTF_8);
        byte[] b = hashB.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }
}
