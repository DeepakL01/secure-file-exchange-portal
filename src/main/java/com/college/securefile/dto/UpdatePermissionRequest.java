package com.college.securefile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class UpdatePermissionRequest {

    @NotBlank(message = "Revocation token is required to authenticate ownership")
    @Size(min = 16, max = 128, message = "Revocation token must be between 16 and 128 characters")
    private String revokeToken;

    @NotNull(message = "downloadEnabled flag is required")
    private Boolean downloadEnabled;

    public UpdatePermissionRequest() {
    }

    public UpdatePermissionRequest(String revokeToken, Boolean downloadEnabled) {
        this.revokeToken = revokeToken;
        this.downloadEnabled = downloadEnabled;
    }

    public String getRevokeToken() {
        return revokeToken;
    }

    public void setRevokeToken(String revokeToken) {
        this.revokeToken = revokeToken;
    }

    public Boolean getDownloadEnabled() {
        return downloadEnabled;
    }

    public void setDownloadEnabled(Boolean downloadEnabled) {
        this.downloadEnabled = downloadEnabled;
    }
}
