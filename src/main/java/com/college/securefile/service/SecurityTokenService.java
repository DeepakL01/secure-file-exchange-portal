package com.college.securefile.service;

import com.college.securefile.util.HashUtils;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Service responsible for generating cryptographically secure random tokens
 * and computing their SHA-256 hashes for safe persistence.
 */
@Service
public class SecurityTokenService {

    private final SecureRandom secureRandom = new SecureRandom();
    private static final int TOKEN_BYTE_LENGTH = 32; // 256-bit entropy

    /**
     * Generates a cryptographically random, URL-safe access or revocation token.
     */
    public String generateSecureToken() {
        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Computes the SHA-256 hash of a token for database storage or lookup.
     */
    public String hashToken(String token) {
        return HashUtils.sha256Hex(token);
    }

    /**
     * Verifies whether a candidate token matches an expected SHA-256 hash in constant time.
     */
    public boolean verifyToken(String candidateToken, String expectedHash) {
        if (candidateToken == null || expectedHash == null) {
            return false;
        }
        String candidateHash = hashToken(candidateToken);
        return HashUtils.constantTimeEquals(candidateHash, expectedHash);
    }
}
