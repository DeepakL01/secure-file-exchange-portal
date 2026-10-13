-- Database initialization script for Secure File Exchange Portal
-- Target Database: MySQL 8.0+

CREATE DATABASE IF NOT EXISTS secure_file_share
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE secure_file_share;

CREATE TABLE IF NOT EXISTS file_metadata (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    original_filename VARCHAR(255) NOT NULL,
    storage_filename VARCHAR(255) NOT NULL UNIQUE,
    content_type VARCHAR(100) DEFAULT 'application/octet-stream',
    ciphertext_size_bytes BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    revoke_token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    download_count INT NOT NULL DEFAULT 0,
    max_downloads INT DEFAULT NULL,
    INDEX idx_file_token_hash (token_hash),
    INDEX idx_file_revoke_hash (revoke_token_hash),
    INDEX idx_file_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
