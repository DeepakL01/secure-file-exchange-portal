package com.college.securefile.repository;

import com.college.securefile.entity.FileMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileMetadataRepository extends JpaRepository<FileMetadata, String> {

    Optional<FileMetadata> findByTokenHash(String tokenHash);

    Optional<FileMetadata> findByRevokeTokenHash(String revokeTokenHash);

    boolean existsByTokenHash(String tokenHash);

    boolean existsByRevokeTokenHash(String revokeTokenHash);

    List<FileMetadata> findByOwnerClientIdOrderByCreatedAtDesc(String ownerClientId);

    long countByOwnerClientId(String ownerClientId);

    long countByOwnerClientIdAndRevokedFalse(String ownerClientId);

    long countByOwnerClientIdAndRevokedTrue(String ownerClientId);
}
