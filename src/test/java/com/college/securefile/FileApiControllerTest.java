package com.college.securefile;

import com.college.securefile.config.StorageProperties;
import com.college.securefile.controller.FileApiController;
import com.college.securefile.controller.GlobalExceptionHandler;
import com.college.securefile.dto.FileMetadataResponse;
import com.college.securefile.dto.RevokeResponse;
import com.college.securefile.exception.InvalidTokenException;
import com.college.securefile.exception.LinkExpiredException;
import com.college.securefile.exception.LinkRevokedException;
import com.college.securefile.service.FileExchangeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class FileApiControllerTest {

    private MockMvc mockMvc;

    @Mock
    private FileExchangeService exchangeService;

    private StorageProperties storageProperties;

    @BeforeEach
    void setUp() {
        storageProperties = new StorageProperties();
        storageProperties.setUploadDir("./target/test-storage");
        storageProperties.setMaxFileSizeBytes(52428800);
        storageProperties.setPublicBaseUrl("https://share.college.edu");

        FileApiController controller = new FileApiController(exchangeService, storageProperties);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/files/config returns publicBaseUrl and max file size")
    void testGetConfig() throws Exception {
        mockMvc.perform(get("/api/files/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicBaseUrl").value("https://share.college.edu"))
                .andExpect(jsonPath("$.maxFileSizeMb").value(50));
    }

    @Test
    @DisplayName("GET /api/files/meta returns 200 and file metadata for valid token")
    void testGetMetadataSuccess() throws Exception {
        FileMetadataResponse response = new FileMetadataResponse(
                "file-uuid-1", "report.pdf", "application/pdf", 1024L,
                Instant.now(), Instant.now().plusSeconds(3600), false, false, 0,
                3, false
        );

        when(exchangeService.getMetadata(eq("valid-token"))).thenReturn(response);

        mockMvc.perform(get("/api/files/meta").param("token", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId").value("file-uuid-1"))
                .andExpect(jsonPath("$.originalFilename").value("report.pdf"))
                .andExpect(jsonPath("$.ciphertextSizeBytes").value(1024))
                .andExpect(jsonPath("$.maxDownloads").value(3));
    }

    @Test
    @DisplayName("GET /api/files/meta returns 404 when token is invalid")
    void testGetMetadataInvalidToken() throws Exception {
        when(exchangeService.getMetadata(eq("bad-token")))
                .thenThrow(new InvalidTokenException("Access token is invalid or does not exist"));

        mockMvc.perform(get("/api/files/meta").param("token", "bad-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Invalid Token"));
    }

    @Test
    @DisplayName("GET /api/files/meta returns 410 when link has expired")
    void testGetMetadataExpired() throws Exception {
        when(exchangeService.getMetadata(eq("expired-token")))
                .thenThrow(new LinkExpiredException("This download link has expired"));

        mockMvc.perform(get("/api/files/meta").param("token", "expired-token"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410))
                .andExpect(jsonPath("$.error").value("Link Expired"));
    }

    @Test
    @DisplayName("GET /api/files/meta returns 410 when link has been revoked")
    void testGetMetadataRevoked() throws Exception {
        when(exchangeService.getMetadata(eq("revoked-token")))
                .thenThrow(new LinkRevokedException("This download link was revoked by the uploader"));

        mockMvc.perform(get("/api/files/meta").param("token", "revoked-token"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410))
                .andExpect(jsonPath("$.error").value("Link Revoked"));
    }

    @Test
    @DisplayName("POST /api/files/revoke returns 200 with confirmation message")
    void testRevokeSuccess() throws Exception {
        RevokeResponse revokeResponse = new RevokeResponse(
                "file-uuid-1", "Revoked successfully", true, Instant.now()
        );

        when(exchangeService.revokeFile(eq("valid-revoke-token-1234567890")))
                .thenReturn(revokeResponse);

        String jsonRequest = "{\"revokeToken\": \"valid-revoke-token-1234567890\"}";

        mockMvc.perform(post("/api/files/revoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId").value("file-uuid-1"))
                .andExpect(jsonPath("$.revoked").value(true));
    }
}
