package com.college.securefile;

import com.college.securefile.entity.FileMetadata;
import com.college.securefile.repository.FileMetadataRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.config.name=application-test")
@AutoConfigureMockMvc
class SecureFileExchangeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FileMetadataRepository repository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Health endpoint returns UP status")
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("secure-file-exchange"));
    }

    @Test
    @DisplayName("Config endpoint returns application configuration")
    void testConfigEndpoint() throws Exception {
        mockMvc.perform(get("/api/files/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxFileSizeBytes").isNumber())
                .andExpect(jsonPath("$.maxFileSizeMb").isNumber());
    }

    @Test
    @DisplayName("Complete end-to-end workflow: Upload -> Meta -> Download -> Status -> Revoke -> Rejection")
    void testCompleteFileExchangeWorkflow() throws Exception {
        // 1. Prepare simulated encrypted binary payload (12-byte IV + ciphertext)
        byte[] simulatedEncryptedBytes = new byte[12 + 128];
        for (int i = 0; i < simulatedEncryptedBytes.length; i++) {
            simulatedEncryptedBytes[i] = (byte) (i ^ 0xAA);
        }

        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "binary-archive.zip.enc",
                "application/octet-stream",
                simulatedEncryptedBytes
        );

        // 2. Perform Upload
        MvcResult uploadResult = mockMvc.perform(multipart("/api/files/upload")
                        .file(multipartFile)
                        .param("originalFilename", "binary-archive.zip")
                        .param("expiryMinutes", "30")
                        .param("maxDownloads", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.revokeToken").isNotEmpty())
                .andExpect(jsonPath("$.originalFilename").value("binary-archive.zip"))
                .andExpect(jsonPath("$.ciphertextSizeBytes").value(simulatedEncryptedBytes.length))
                .andExpect(jsonPath("$.maxDownloads").value(5))
                .andReturn();

        JsonNode uploadJson = objectMapper.readTree(uploadResult.getResponse().getContentAsString());
        String accessToken = uploadJson.get("accessToken").asText();
        String revokeToken = uploadJson.get("revokeToken").asText();
        String fileId = uploadJson.get("fileId").asText();

        assertNotNull(accessToken);
        assertNotNull(revokeToken);
        assertFalse(accessToken.isBlank());
        assertFalse(revokeToken.isBlank());

        // 3. Verify Database State:
        // Plaintext tokens and decryption keys must NOT be stored in the database!
        Optional<FileMetadata> dbEntityOpt = repository.findById(fileId);
        assertTrue(dbEntityOpt.isPresent());
        FileMetadata dbEntity = dbEntityOpt.get();

        assertNotEquals(accessToken, dbEntity.getTokenHash(), "Access token must be hashed in the database");
        assertNotEquals(revokeToken, dbEntity.getRevokeTokenHash(), "Revoke token must be hashed in the database");
        assertEquals(64, dbEntity.getTokenHash().length(), "Token hash must be SHA-256 (64 hex characters)");
        assertEquals(64, dbEntity.getRevokeTokenHash().length(), "Revoke token hash must be SHA-256 (64 hex characters)");
        assertFalse(dbEntity.isRevoked(), "Newly uploaded file should not be revoked");
        assertFalse(dbEntity.isExpired(), "Newly uploaded file should not be expired");
        assertEquals(5, dbEntity.getMaxDownloads());

        // 4. Retrieve Metadata using Access Token
        mockMvc.perform(get("/api/files/meta").param("token", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId").value(fileId))
                .andExpect(jsonPath("$.originalFilename").value("binary-archive.zip"))
                .andExpect(jsonPath("$.ciphertextSizeBytes").value(simulatedEncryptedBytes.length))
                .andExpect(jsonPath("$.revoked").value(false))
                .andExpect(jsonPath("$.expired").value(false))
                .andExpect(jsonPath("$.maxDownloads").value(5));

        // 5. Download Encrypted Ciphertext using Access Token & Verify Binary Integrity
        MvcResult downloadResult = mockMvc.perform(get("/api/files/download").param("token", accessToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/octet-stream"))
                .andExpect(header().exists("Content-Disposition"))
                .andReturn();

        byte[] downloadedBytes = downloadResult.getResponse().getContentAsByteArray();
        assertArrayEquals(simulatedEncryptedBytes, downloadedBytes, "Downloaded binary payload must match uploaded bytes exactly");

        // 6. Check File Status using Revocation Token
        mockMvc.perform(get("/api/files/status").param("revokeToken", revokeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId").value(fileId))
                .andExpect(jsonPath("$.downloadCount").value(1));

        // 7. Revoke File using Revocation Token
        String revokePayload = "{\"revokeToken\": \"" + revokeToken + "\"}";
        mockMvc.perform(post("/api/files/revoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(revokePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId").value(fileId))
                .andExpect(jsonPath("$.revoked").value(true));

        // 8. Confirm Revocation Status in Database
        FileMetadata revokedEntity = repository.findById(fileId).orElseThrow();
        assertTrue(revokedEntity.isRevoked(), "Entity must be marked revoked");

        // 9. Subsequent Metadata and Download Requests must be Rejected (HTTP 410 Gone)
        mockMvc.perform(get("/api/files/meta").param("token", accessToken))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error").value("Link Revoked"));

        mockMvc.perform(get("/api/files/download").param("token", accessToken))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error").value("Link Revoked"));
    }

    @Test
    @DisplayName("Download limit enforcement: 1st download succeeds, 2nd download rejected with 410 Gone")
    void testDownloadLimitEnforcement() throws Exception {
        byte[] payload = "one-time-payload-bytes".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "secret-doc.pdf.enc", "application/octet-stream", payload
        );

        // Upload with maxDownloads = 1 (single-use self-destruct)
        MvcResult uploadResult = mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("originalFilename", "secret-doc.pdf")
                        .param("expiryMinutes", "60")
                        .param("maxDownloads", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxDownloads").value(1))
                .andReturn();

        JsonNode json = objectMapper.readTree(uploadResult.getResponse().getContentAsString());
        String accessToken = json.get("accessToken").asText();

        // 1st Download should succeed
        mockMvc.perform(get("/api/files/download").param("token", accessToken))
                .andExpect(status().isOk());

        // 2nd Download should be rejected because limit (1) was reached
        mockMvc.perform(get("/api/files/download").param("token", accessToken))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error").value("Download Limit Reached"));

        // Metadata check should also indicate limit reached
        mockMvc.perform(get("/api/files/meta").param("token", accessToken))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error").value("Download Limit Reached"));
    }

    @Test
    @DisplayName("Download fails with 410 Gone when file link has expired")
    void testExpiredFileRejection() throws Exception {
        byte[] payload = "expired-sample-data".getBytes();
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", "expired.txt", "application/octet-stream", payload
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/files/upload")
                        .file(multipartFile)
                        .param("originalFilename", "expired.txt")
                        .param("expiryMinutes", "5"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode uploadJson = objectMapper.readTree(uploadResult.getResponse().getContentAsString());
        String accessToken = uploadJson.get("accessToken").asText();
        String fileId = uploadJson.get("fileId").asText();

        // Artificially expire the record in the database
        FileMetadata entity = repository.findById(fileId).orElseThrow();
        entity.setExpiresAt(Instant.now().minus(10, ChronoUnit.MINUTES));
        repository.save(entity);

        // Verify that access is rejected
        mockMvc.perform(get("/api/files/meta").param("token", accessToken))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error").value("Link Expired"));

        mockMvc.perform(get("/api/files/download").param("token", accessToken))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error").value("Link Expired"));
    }

    @Test
    @DisplayName("Invalid token requests return 404 Not Found")
    void testInvalidTokenReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/files/meta").param("token", "non-existent-token-abc"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Invalid Token"));

        mockMvc.perform(get("/api/files/download").param("token", "non-existent-token-abc"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Invalid Token"));

        mockMvc.perform(get("/api/files/status").param("revokeToken", "non-existent-token-abc"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Invalid Token"));
    }
}
