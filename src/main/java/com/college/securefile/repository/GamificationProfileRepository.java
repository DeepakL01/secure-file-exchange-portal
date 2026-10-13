package com.college.securefile.repository;

import com.college.securefile.entity.GamificationProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GamificationProfileRepository extends JpaRepository<GamificationProfile, String> {
    Optional<GamificationProfile> findByClientId(String clientId);
}
