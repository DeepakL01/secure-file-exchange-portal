package com.college.securefile.dto;

import java.time.Instant;

public class UploadResponse {

    private String fileId;
    private String originalFilename;
    private Long ciphertextSizeBytes;
    private String accessToken;
    private String revokeToken;
    private Instant createdAt;
    private Instant expiresAt;
    private Integer maxDownloads;
    private boolean downloadEnabled;

    public UploadResponse() {
    }

    public UploadResponse(String fileId, String originalFilename, Long ciphertextSizeBytes,
                          String accessToken, String revokeToken, Instant createdAt,
                          Instant expiresAt, Integer maxDownloads, boolean downloadEnabled) {
        this.fileId = fileId;
        this.originalFilename = originalFilename;
        this.ciphertextSizeBytes = ciphertextSizeBytes;
        this.accessToken = accessToken;
        this.revokeToken = revokeToken;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.maxDownloads = maxDownloads;
        this.downloadEnabled = downloadEnabled;
    }
public UploadResponse(String fileId, String originalFilename, Long ciphertextSizeBytes,
                      String accessToken, String revokeToken, Instant createdAt,
                      Instant expiresAt, Integer maxDownloads) {
    this(fileId, originalFilename, ciphertextSizeBytes, accessToken,
         revokeToken, createdAt, expiresAt, maxDownloads, true);
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

    public Long getCiphertextSizeBytes() {
        return ciphertextSizeBytes;
    }

    public void setCiphertextSizeBytes(Long ciphertextSizeBytes) {
        this.ciphertextSizeBytes = ciphertextSizeBytes;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRevokeToken() {
        return revokeToken;
    }

    public void setRevokeToken(String revokeToken) {
        this.revokeToken = revokeToken;
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
}
