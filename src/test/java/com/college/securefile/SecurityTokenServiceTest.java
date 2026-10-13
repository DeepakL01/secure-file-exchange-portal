package com.college.securefile;

import com.college.securefile.service.SecurityTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecurityTokenServiceTest {

    private SecurityTokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new SecurityTokenService();
    }

    @Test
    @DisplayName("Generated tokens should be high entropy, non-empty, and unique")
    void testGenerateSecureTokenUniqueness() {
        String token1 = tokenService.generateSecureToken();
        String token2 = tokenService.generateSecureToken();

        assertNotNull(token1);
        assertNotNull(token2);
        assertNotEquals(token1, token2, "Consecutive random tokens must be distinct");
        assertTrue(token1.length() >= 32, "Token should be sufficiently long");
    }

    @Test
    @DisplayName("SHA-256 hash output should be a 64-character lowercase hex string")
    void testHashTokenOutputFormat() {
        String token = "sample-secret-token-xyz-123";
        String hash = tokenService.hashToken(token);

        assertNotNull(hash);
        assertEquals(64, hash.length(), "SHA-256 hex hash must be exactly 64 characters long");
        assertTrue(hash.matches("^[0-9a-f]{64}$"), "Hash must only contain lowercase hexadecimal characters");
    }

    @Test
    @DisplayName("Hashing should be deterministic for identical input")
    void testHashDeterministic() {
        String token = "constant-test-token-value";
        String hash1 = tokenService.hashToken(token);
        String hash2 = tokenService.hashToken(token);

        assertEquals(hash1, hash2, "Identical tokens must yield identical SHA-256 hashes");
    }

    @Test
    @DisplayName("verifyToken should succeed for valid match and fail for tampered token")
    void testVerifyToken() {
        String token = tokenService.generateSecureToken();
        String expectedHash = tokenService.hashToken(token);

        assertTrue(tokenService.verifyToken(token, expectedHash));
        assertFalse(tokenService.verifyToken(token + "-tampered", expectedHash));
        assertFalse(tokenService.verifyToken(null, expectedHash));
    }
}
