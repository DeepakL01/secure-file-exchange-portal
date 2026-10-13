package com.college.securefile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RevokeRequest {

    @NotBlank(message = "Revocation token is required")
    @Size(min = 16, max = 128, message = "Revocation token must be between 16 and 128 characters")
    private String revokeToken;

    public RevokeRequest() {
    }

    public RevokeRequest(String revokeToken) {
        this.revokeToken = revokeToken;
    }

    public String getRevokeToken() {
        return revokeToken;
    }

    public void setRevokeToken(String revokeToken) {
        this.revokeToken = revokeToken;
    }
}
