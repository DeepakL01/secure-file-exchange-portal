package com.college.securefile;

import com.college.securefile.dto.FileMetadataResponse;
import com.college.securefile.dto.RevokeResponse;
import com.college.securefile.dto.UploadResponse;
import com.college.securefile.entity.FileMetadata;
import com.college.securefile.exception.DownloadLimitExceededException;
import com.college.securefile.exception.InvalidTokenException;
import com.college.securefile.exception.LinkExpiredException;
import com.college.securefile.exception.LinkRevokedException;
import com.college.securefile.repository.FileMetadataRepository;
import com.college.securefile.service.FileExchangeService;
import com.college.securefile.service.FileStorageService;
import com.college.securefile.service.SecurityTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileExchangeServiceTest {

    @Mock
    private FileMetadataRepository repository;

    @Mock
    private FileStorageService storageService;

    private SecurityTokenService tokenService;
    private FileExchangeService exchangeService;

    @BeforeEach
    void setUp() {
        tokenService = new SecurityTokenService();
        exchangeService = new FileExchangeService(repository, storageService, tokenService);
    }

    @Test
    @DisplayName("Upload stores ciphertext with server UUID, hashes tokens, and returns plain tokens only in DTO")
    void testUploadEncryptedFile() {
        byte[] fakeCiphertext = "encrypted-bytes-payload".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "original.pdf", "application/octet-stream", fakeCiphertext);

        when(storageService.storeFile(any())).thenReturn("stored-uuid-123.enc");
        when(repository.save(any(FileMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UploadResponse response = exchangeService.uploadEncryptedFile(file, "original.pdf", 30, 5);

        assertNotNull(response);
        assertNotNull(response.getAccessToken(), "Plaintext access token must be returned to uploader");
        assertNotNull(response.getRevokeToken(), "Plaintext revoke token must be returned to uploader");
        assertEquals("original.pdf", response.getOriginalFilename());
        assertEquals(5, response.getMaxDownloads());

        // Verify that the repository received hashed tokens, not plaintext tokens
        verify(repository).save(argThat(entity -> {
            boolean tokenHashed = !entity.getTokenHash().equals(response.getAccessToken())
                    && entity.getTokenHash().length() == 64;
            boolean revokeHashed = !entity.getRevokeTokenHash().equals(response.getRevokeToken())
                    && entity.getRevokeTokenHash().length() == 64;
            return tokenHashed && revokeHashed;
        }));
    }

    @Test
    @DisplayName("Validate access token successfully when link is active and valid")
    void testValidateAccessTokenSuccess() {
        String plainToken = "test-active-token";
        String tokenHash = tokenService.hashToken(plainToken);

        FileMetadata metadata = new FileMetadata(
                "doc.txt", "uuid.enc", "text/plain", 100L,
                tokenHash, "revoke-hash",
                Instant.now().plus(1, ChronoUnit.HOURS), null
        );

        when(repository.findByTokenHash(tokenHash)).thenReturn(Optional.of(metadata));

        FileMetadata result = exchangeService.validateAndGetByAccessToken(plainToken);
        assertNotNull(result);
        assertEquals("doc.txt", result.getOriginalFilename());
    }

    @Test
    @DisplayName("Reject download request if token does not exist in database")
    void testValidateInvalidTokenThrowsException() {
        String nonExistentToken = "unknown-token-123";
        String tokenHash = tokenService.hashToken(nonExistentToken);

        when(repository.findByTokenHash(tokenHash)).thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class, () ->
                exchangeService.validateAndGetByAccessToken(nonExistentToken));
    }

    @Test
    @DisplayName("Reject download request if link has expired")
    void testValidateExpiredLinkThrowsException() {
        String plainToken = "expired-token-xyz";
        String tokenHash = tokenService.hashToken(plainToken);

        FileMetadata expiredMetadata = new FileMetadata(
                "doc.txt", "uuid.enc", "text/plain", 100L,
                tokenHash, "revoke-hash",
                Instant.now().minus(5, ChronoUnit.MINUTES), null
        );

        when(repository.findByTokenHash(tokenHash)).thenReturn(Optional.of(expiredMetadata));

        assertThrows(LinkExpiredException.class, () ->
                exchangeService.validateAndGetByAccessToken(plainToken));
    }

    @Test
    @DisplayName("Reject download request if link has been revoked")
    void testValidateRevokedLinkThrowsException() {
        String plainToken = "revoked-token-xyz";
        String tokenHash = tokenService.hashToken(plainToken);

        FileMetadata revokedMetadata = new FileMetadata(
                "doc.txt", "uuid.enc", "text/plain", 100L,
                tokenHash, "revoke-hash",
                Instant.now().plus(1, ChronoUnit.HOURS), null
        );
        revokedMetadata.setRevoked(true);

        when(repository.findByTokenHash(tokenHash)).thenReturn(Optional.of(revokedMetadata));

        assertThrows(LinkRevokedException.class, () ->
                exchangeService.validateAndGetByAccessToken(plainToken));
    }

    @Test
    @DisplayName("Reject download request if download limit has been reached")
    void testValidateDownloadLimitReachedThrowsException() {
        String plainToken = "limit-reached-token";
        String tokenHash = tokenService.hashToken(plainToken);

        FileMetadata limitMetadata = new FileMetadata(
                "doc.txt", "uuid.enc", "text/plain", 100L,
                tokenHash, "revoke-hash",
                Instant.now().plus(1, ChronoUnit.HOURS), 1
        );
        limitMetadata.setDownloadCount(1); // 1 of 1 download already used

        when(repository.findByTokenHash(tokenHash)).thenReturn(Optional.of(limitMetadata));

        assertThrows(DownloadLimitExceededException.class, () ->
                exchangeService.validateAndGetByAccessToken(plainToken));
    }

    @Test
    @DisplayName("Revoke file immediately marks entity as revoked and deletes ciphertext from disk")
    void testRevokeFileSuccess() {
        String plainRevokeToken = "my-secret-revoke-token";
        String revokeHash = tokenService.hashToken(plainRevokeToken);

        FileMetadata activeMetadata = new FileMetadata(
                "secret.pdf", "stored-to-delete.enc", "application/pdf", 250L,
                "token-hash", revokeHash,
                Instant.now().plus(2, ChronoUnit.HOURS), null
        );

        when(repository.findByRevokeTokenHash(revokeHash)).thenReturn(Optional.of(activeMetadata));
        when(repository.save(any(FileMetadata.class))).thenAnswer(i -> i.getArgument(0));

        RevokeResponse response = exchangeService.revokeFile(plainRevokeToken);

        assertTrue(response.isRevoked());
        assertTrue(activeMetadata.isRevoked());
        verify(storageService).deleteFile("stored-to-delete.enc");
    }

    @Test
    @DisplayName("Revocation fails if revoke token is invalid")
    void testRevokeInvalidTokenThrowsException() {
        String wrongRevokeToken = "bad-revoke-token";
        String wrongHash = tokenService.hashToken(wrongRevokeToken);

        when(repository.findByRevokeTokenHash(wrongHash)).thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class, () ->
                exchangeService.revokeFile(wrongRevokeToken));
    }
}
