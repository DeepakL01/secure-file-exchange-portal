package com.college.securefile.dto;

import java.time.Instant;

public class FileMetadataResponse {

    private String fileId;
    private String originalFilename;
    private String contentType;
    private Long ciphertextSizeBytes;
    private Instant createdAt;
    private Instant expiresAt;
    private boolean revoked;
    private boolean expired;
    private int downloadCount;
    private Integer maxDownloads;
    private boolean downloadLimitReached;

    public FileMetadataResponse() {
    }

    public FileMetadataResponse(String fileId, String originalFilename, String contentType,
                                Long ciphertextSizeBytes, Instant createdAt, Instant expiresAt,
                                boolean revoked, boolean expired, int downloadCount,
                                Integer maxDownloads, boolean downloadLimitReached) {
        this.fileId = fileId;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.ciphertextSizeBytes = ciphertextSizeBytes;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.revoked = revoked;
        this.expired = expired;
        this.downloadCount = downloadCount;
        this.maxDownloads = maxDownloads;
        this.downloadLimitReached = downloadLimitReached;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
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

    public boolean isExpired() {
        return expired;
    }

    public void setExpired(boolean expired) {
        this.expired = expired;
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

    public boolean isDownloadLimitReached() {
        return downloadLimitReached;
    }

    public void setDownloadLimitReached(boolean downloadLimitReached) {
        this.downloadLimitReached = downloadLimitReached;
    }
}
