package com.college.securefile.service;

import com.college.securefile.dto.FileMetadataResponse;
import com.college.securefile.dto.RevokeResponse;
import com.college.securefile.dto.UploadResponse;
import com.college.securefile.entity.FileMetadata;
import com.college.securefile.exception.DownloadLimitExceededException;
import com.college.securefile.exception.InvalidInputException;
import com.college.securefile.exception.InvalidTokenException;
import com.college.securefile.exception.LinkExpiredException;
import com.college.securefile.exception.LinkRevokedException;
import com.college.securefile.repository.FileMetadataRepository;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;

/**
 * Core business service orchestrating secure file exchange operations.
 * Validates tokens, monitors expiry, processes revocations, enforces download limits,
 * and interfaces with the encrypted storage.
 */
@Service
public class FileExchangeService {

    private final FileMetadataRepository repository;
    private final FileStorageService storageService;
    private final SecurityTokenService tokenService;

    private static final int MIN_EXPIRY_MINUTES = 1;
    private static final int MAX_EXPIRY_MINUTES = 10080; // 7 days
    private static final int DEFAULT_EXPIRY_MINUTES = 60; // 1 hour

    public FileExchangeService(FileMetadataRepository repository,
                               FileStorageService storageService,
                               SecurityTokenService tokenService) {
        this.repository = repository;
        this.storageService = storageService;
        this.tokenService = tokenService;
    }

    /**
     * Backward-compatible upload method defaulting to unlimited downloads.
     */
    @Transactional
    public UploadResponse uploadEncryptedFile(MultipartFile file, String originalFilename, Integer expiryMinutes) {
        return uploadEncryptedFile(file, originalFilename, expiryMinutes, null);
    }

    /**
     * Handles upload of client-side encrypted file payload with configurable expiry and download limits.
     */
    @Transactional
    public UploadResponse uploadEncryptedFile(MultipartFile file, String originalFilename,
                                              Integer expiryMinutes, Integer maxDownloads) {
        if (file == null || file.isEmpty()) {
            throw new InvalidInputException("Encrypted file payload must not be empty");
        }

        // Sanitize original filename (remove paths, retain base name, limit length)
        String sanitizedFilename = sanitizeFilename(originalFilename);

        // Validate expiry
        int effectiveExpiryMinutes = (expiryMinutes != null && expiryMinutes >= MIN_EXPIRY_MINUTES)
                ? Math.min(expiryMinutes, MAX_EXPIRY_MINUTES)
                : DEFAULT_EXPIRY_MINUTES;

        // Normalize max downloads (null or <= 0 means unlimited)
        Integer effectiveMaxDownloads = (maxDownloads != null && maxDownloads > 0)
                ? Math.min(maxDownloads, 1000)
                : null;

        // Generate high-entropy access and revoke tokens
        String accessToken = tokenService.generateSecureToken();
        String revokeToken = tokenService.generateSecureToken();

        // Hash tokens before saving to database (SHA-256)
        String tokenHash = tokenService.hashToken(accessToken);
        String revokeTokenHash = tokenService.hashToken(revokeToken);

        // Store ciphertext on disk with server-assigned UUID
        String storageFilename = storageService.storeFile(file);

        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(effectiveExpiryMinutes));

        FileMetadata metadata = new FileMetadata(
                sanitizedFilename,
                storageFilename,
                file.getContentType(),
                file.getSize(),
                tokenHash,
                revokeTokenHash,
                expiresAt,
                effectiveMaxDownloads
        );

        FileMetadata saved = repository.save(metadata);

        return new UploadResponse(
                saved.getId(),
                saved.getOriginalFilename(),
                saved.getCiphertextSizeBytes(),
                accessToken,
                revokeToken,
                saved.getCreatedAt(),
                saved.getExpiresAt(),
                saved.getMaxDownloads()
        );
    }

    /**
     * Retrieves file metadata using the access token. Validates expiry, revocation, and download limits.
     */
    @Transactional(readOnly = true)
    public FileMetadataResponse getMetadata(String accessToken) {
        FileMetadata metadata = validateAndGetByAccessToken(accessToken);
        return toResponse(metadata);
    }

    /**
     * Validates access token, enforces download limits, and streams the encrypted ciphertext resource.
     */
    @Transactional
    public DownloadPayload downloadEncryptedFile(String accessToken) {
        FileMetadata metadata = validateAndGetByAccessToken(accessToken);

        // Update download metrics atomically
        metadata.incrementDownloadCount();
        repository.save(metadata);

        Resource resource = storageService.loadFileAsResource(metadata.getStorageFilename());
        return new DownloadPayload(resource, metadata);
    }

    /**
     * Revokes a file link immediately using the revocation token.
     */
    @Transactional
    public RevokeResponse revokeFile(String revokeToken) {
        if (revokeToken == null || revokeToken.isBlank()) {
            throw new InvalidInputException("Revocation token must be provided");
        }

        String revokeHash = tokenService.hashToken(revokeToken.trim());
        FileMetadata metadata = repository.findByRevokeTokenHash(revokeHash)
                .orElseThrow(() -> new InvalidTokenException("Invalid revocation token or file not found"));

        if (!metadata.isRevoked()) {
            metadata.setRevoked(true);
            repository.save(metadata);

            // Securely wipe ciphertext file from disk upon revocation
            storageService.deleteFile(metadata.getStorageFilename());
        }

        return new RevokeResponse(
                metadata.getId(),
                "File link has been successfully revoked and ciphertext purged from server storage.",
                true,
                Instant.now()
        );
    }

    /**
     * Retrieves metadata via the revocation token (for uploader dashboard / audit).
     */
    @Transactional(readOnly = true)
    public FileMetadataResponse getStatusByRevokeToken(String revokeToken) {
        if (revokeToken == null || revokeToken.isBlank()) {
            throw new InvalidInputException("Revocation token must be provided");
        }
        String revokeHash = tokenService.hashToken(revokeToken.trim());
        FileMetadata metadata = repository.findByRevokeTokenHash(revokeHash)
                .orElseThrow(() -> new InvalidTokenException("Invalid revocation token or file not found"));

        return toResponse(metadata);
    }

    /**
     * Validates access token, checking token existence, revocation, expiry, and download limits.
     */
    public FileMetadata validateAndGetByAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new InvalidTokenException("Access token cannot be empty");
        }

        String tokenHash = tokenService.hashToken(accessToken.trim());
        FileMetadata metadata = repository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidTokenException("Access token is invalid or does not exist"));

        if (metadata.isRevoked()) {
            throw new LinkRevokedException("This download link was revoked by the uploader and is no longer accessible");
        }

        if (metadata.isExpired()) {
            throw new LinkExpiredException("This download link has expired and can no longer be accessed");
        }

        if (metadata.isDownloadLimitReached()) {
            throw new DownloadLimitExceededException(
                    "This download link reached its maximum download limit (" + metadata.getMaxDownloads() + ") and is no longer accessible"
            );
        }

        return metadata;
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "encrypted_payload.bin";
        }
        // Extract base filename without directories
        String baseName = Paths.get(filename).getFileName().toString();
        // Strip non-printable or dangerous shell characters while keeping extension
        String sanitized = baseName.replaceAll("[^a-zA-Z0-9._\\- ]", "_");
        if (sanitized.isBlank()) {
            return "encrypted_payload.bin";
        }
        if (sanitized.length() > 250) {
            return sanitized.substring(0, 250);
        }
        return sanitized;
    }

    private FileMetadataResponse toResponse(FileMetadata m) {
        return new FileMetadataResponse(
                m.getId(),
                m.getOriginalFilename(),
                m.getContentType(),
                m.getCiphertextSizeBytes(),
                m.getCreatedAt(),
                m.getExpiresAt(),
                m.isRevoked(),
                m.isExpired(),
                m.getDownloadCount(),
                m.getMaxDownloads(),
                m.isDownloadLimitReached()
        );
    }

    /**
     * Container record for streaming download payloads.
     */
    public record DownloadPayload(Resource resource, FileMetadata metadata) {}
}
