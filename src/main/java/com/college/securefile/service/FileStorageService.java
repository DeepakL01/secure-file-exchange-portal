package com.college.securefile.service;

import com.college.securefile.config.StorageProperties;
import com.college.securefile.exception.FileStorageException;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Service handling filesystem storage of encrypted ciphertext files.
 * Protects against path traversal, enforces storage directory boundaries,
 * and assigns server-generated random identifiers.
 */
@Service
public class FileStorageService {

    private final Path storageDirectory;
    private final long maxFileSizeBytes;

    public FileStorageService(StorageProperties properties) {
        this.storageDirectory = properties.getUploadPath();
        this.maxFileSizeBytes = properties.getMaxFileSizeBytes();
    }

    @PostConstruct
    public void init() {
        try {
            if (!Files.exists(storageDirectory)) {
                Files.createDirectories(storageDirectory);
            }
        } catch (IOException e) {
            throw new FileStorageException("Could not initialize storage directory: " + storageDirectory, e);
        }
    }

    /**
     * Stores an uploaded encrypted payload on disk using a server-generated UUID.
     *
     * @param file the uploaded multipart file
     * @return the unique storage filename
     */
    public String storeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileStorageException("Cannot store empty or null file");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new FileStorageException("File size exceeds maximum allowed limit (" + (maxFileSizeBytes / (1024 * 1024)) + " MB)");
        }

        // Generate server-controlled unique filename (never use user-supplied filename on disk)
        String storageFilename = UUID.randomUUID().toString() + ".enc";
        Path targetLocation = resolveAndValidate(storageFilename);

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING);
            return storageFilename;
        } catch (IOException e) {
            throw new FileStorageException("Failed to store encrypted file on disk", e);
        }
    }

    /**
     * Loads a file resource for download.
     *
     * @param storageFilename the server-generated filename
     * @return the Resource
     */
    public Resource loadFileAsResource(String storageFilename) {
        Path filePath = resolveAndValidate(storageFilename);
        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new FileStorageException("Encrypted file not found on disk or not readable: " + storageFilename);
            }
        } catch (MalformedURLException e) {
            throw new FileStorageException("File path invalid for: " + storageFilename, e);
        }
    }

    /**
     * Deletes a stored file if it exists.
     */
    public boolean deleteFile(String storageFilename) {
        if (storageFilename == null || storageFilename.isBlank()) {
            return false;
        }
        try {
            Path filePath = resolveAndValidate(storageFilename);
            return Files.deleteIfExists(filePath);
        } catch (Exception e) {
            // Logically handle failure without breaking calling business logic
            return false;
        }
    }

    /**
     * Resolves a filename against storage root and strictly prevents path traversal.
     */
    public Path resolveAndValidate(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new FileStorageException("Filename cannot be blank");
        }
        Path resolvedPath = storageDirectory.resolve(filename).normalize();
        if (!resolvedPath.startsWith(storageDirectory)) {
            throw new FileStorageException("Path traversal attempt detected for filename: " + filename);
        }
        return resolvedPath;
    }

    public Path getStorageDirectory() {
        return storageDirectory;
    }
}
