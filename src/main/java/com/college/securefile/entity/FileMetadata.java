package com.college.securefile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA Entity representing the stored file metadata.
 * Note: Never contains the plaintext file contents or client-side encryption keys.
 */
@Entity
@Table(
    name = "file_metadata",
    indexes = {
        @Index(name = "idx_file_token_hash", columnList = "token_hash"),
        @Index(name = "idx_file_revoke_hash", columnList = "revoke_token_hash"),
        @Index(name = "idx_file_expires_at", columnList = "expires_at"),
        @Index(name = "idx_file_owner_client", columnList = "owner_client_id")
    }
)
public class FileMetadata {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "storage_filename", nullable = false, unique = true, length = 255)
    private String storageFilename;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "ciphertext_size_bytes", nullable = false)
    private Long ciphertextSizeBytes;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "revoke_token_hash", nullable = false, unique = true, length = 64)
    private String revokeTokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked", nullable = false)
    private boolean revoked = false;

    @Column(name = "download_count", nullable = false)
    private int downloadCount = 0;

    @Column(name = "max_downloads")
    private Integer maxDownloads; // null = unlimited

    @Column(name = "download_enabled", nullable = false)
    private boolean downloadEnabled = true;

    @Column(name = "owner_client_id", length = 64)
    private String ownerClientId;

    public FileMetadata() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
        this.downloadEnabled = true;
    }

    public FileMetadata(String originalFilename, String storageFilename, String contentType,
                        Long ciphertextSizeBytes, String tokenHash, String revokeTokenHash,
                        Instant expiresAt, Integer maxDownloads, boolean downloadEnabled,
                        String ownerClientId) {
        this();
        this.originalFilename = originalFilename;
        this.storageFilename = storageFilename;
        this.contentType = (contentType == null || contentType.isBlank()) ? "application/octet-stream" : contentType;
        this.ciphertextSizeBytes = ciphertextSizeBytes;
        this.tokenHash = tokenHash;
        this.revokeTokenHash = revokeTokenHash;
        this.expiresAt = expiresAt;
        this.revoked = false;
        this.downloadCount = 0;
        this.maxDownloads = maxDownloads;
        this.downloadEnabled = downloadEnabled;
        this.ownerClientId = ownerClientId;
    }
    
public FileMetadata(String originalFilename, String storageFilename, String contentType,
                    Long ciphertextSizeBytes, String tokenHash, String revokeTokenHash,
                    Instant expiresAt, Integer maxDownloads) {
    this(originalFilename, storageFilename, contentType, ciphertextSizeBytes,
         tokenHash, revokeTokenHash, expiresAt, maxDownloads, true, null);
}


    public boolean isExpired() {
        return Instant.now().isAfter(this.expiresAt);
    }

    public boolean isDownloadLimitReached() {
        return maxDownloads != null && maxDownloads > 0 && downloadCount >= maxDownloads;
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getStorageFilename() {
        return storageFilename;
    }

    public void setStorageFilename(String storageFilename) {
        this.storageFilename = storageFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getCiphertextSizeBytes() {
        return ciphertextSizeBytes;
    }

    public void setCiphertextSizeBytes(Long ciphertextSizeBytes) {
        this.ciphertextSizeBytes = ciphertextSizeBytes;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public String getRevokeTokenHash() {
        return revokeTokenHash;
    }

    public void setRevokeTokenHash(String revokeTokenHash) {
        this.revokeTokenHash = revokeTokenHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    public int getDownloadCount() {
        return downloadCount;
    }

    public void setDownloadCount(int downloadCount) {
        this.downloadCount = downloadCount;
    }

    public Integer getMaxDownloads() {
        return maxDownloads;
    }

    public void setMaxDownloads(Integer maxDownloads) {
        this.maxDownloads = maxDownloads;
    }

    public boolean isDownloadEnabled() {
        return downloadEnabled;
    }

    public void setDownloadEnabled(boolean downloadEnabled) {
        this.downloadEnabled = downloadEnabled;
    }

    public String getOwnerClientId() {
        return ownerClientId;
    }

    public void setOwnerClientId(String ownerClientId) {
        this.ownerClientId = ownerClientId;
    }

    public void incrementDownloadCount() {
        this.downloadCount++;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FileMetadata that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
