package com.college.securefile.controller;

import com.college.securefile.config.StorageProperties;
import com.college.securefile.dto.FileMetadataResponse;
import com.college.securefile.dto.RevokeRequest;
import com.college.securefile.dto.RevokeResponse;
import com.college.securefile.dto.UploadResponse;
import com.college.securefile.service.FileExchangeService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileApiController {

    private final FileExchangeService exchangeService;
    private final StorageProperties storageProperties;

    public FileApiController(FileExchangeService exchangeService, StorageProperties storageProperties) {
        this.exchangeService = exchangeService;
        this.storageProperties = storageProperties;
    }

    /**
     * Uploads an already-encrypted ciphertext file.
     * The backend never receives the encryption key or plaintext.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "originalFilename", required = false) String originalFilename,
            @RequestParam(value = "expiryMinutes", required = false, defaultValue = "60") Integer expiryMinutes,
            @RequestParam(value = "maxDownloads", required = false) Integer maxDownloads) {

        UploadResponse response = exchangeService.uploadEncryptedFile(file, originalFilename, expiryMinutes, maxDownloads);
        return ResponseEntity.ok(response);
    }

    /**
     * Exposes public base URL and application limits for multi-device / network clients.
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getClientConfig() {
        String publicBaseUrl = storageProperties.getPublicBaseUrl();
        return ResponseEntity.ok(Map.of(
                "publicBaseUrl", (publicBaseUrl != null) ? publicBaseUrl.trim() : "",
                "maxFileSizeBytes", storageProperties.getMaxFileSizeBytes(),
                "maxFileSizeMb", storageProperties.getMaxFileSizeBytes() / (1024 * 1024)
        ));
    }

    /**
     * Retrieves file metadata without downloading ciphertext.
     * Validates that the access token is valid, active, unexpired, and within download limits.
     */
    @GetMapping("/meta")
    public ResponseEntity<FileMetadataResponse> getMetadata(@RequestParam("token") String token) {
        FileMetadataResponse response = exchangeService.getMetadata(token);
        return ResponseEntity.ok(response);
    }

    /**
     * Downloads the encrypted ciphertext payload.
     * The browser will decrypt this payload locally using its fragment key.
     */
    @GetMapping("/download")
    public ResponseEntity<Resource> downloadFile(@RequestParam("token") String token) {
        FileExchangeService.DownloadPayload payload = exchangeService.downloadEncryptedFile(token);

        String safeContentDisposition = "attachment; filename=\""
                + payload.metadata().getStorageFilename() + "\"";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, safeContentDisposition)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .contentLength(payload.metadata().getCiphertextSizeBytes())
                .body(payload.resource());
    }

    /**
     * Revokes a file link immediately using the uploader's revocation token.
     */
    @PostMapping("/revoke")
    public ResponseEntity<RevokeResponse> revokeFile(@Valid @RequestBody RevokeRequest request) {
        RevokeResponse response = exchangeService.revokeFile(request.getRevokeToken());
        return ResponseEntity.ok(response);
    }

    /**
     * Queries status and download metrics using the uploader's revocation token.
     */
    @GetMapping("/status")
    public ResponseEntity<FileMetadataResponse> getStatus(@RequestParam("revokeToken") String revokeToken) {
        FileMetadataResponse response = exchangeService.getStatusByRevokeToken(revokeToken);
        return ResponseEntity.ok(response);
    }
}
