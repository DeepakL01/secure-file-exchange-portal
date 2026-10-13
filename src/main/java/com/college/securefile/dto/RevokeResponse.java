package com.college.securefile.dto;

import java.time.Instant;

public class RevokeResponse {

    private String fileId;
    private String message;
    private boolean revoked;
    private Instant timestamp;

    public RevokeResponse() {
    }

    public RevokeResponse(String fileId, String message, boolean revoked, Instant timestamp) {
        this.fileId = fileId;
        this.message = message;
        this.revoked = revoked;
        this.timestamp = timestamp;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
