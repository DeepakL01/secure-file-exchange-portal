package com.college.securefile.repository;

import com.college.securefile.entity.GamificationActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface GamificationActivityRepository extends JpaRepository<GamificationActivity, String> {
    Optional<GamificationActivity> findByActionKey(String actionKey);
    boolean existsByActionKey(String actionKey);
    List<GamificationActivity> findTop15ByClientIdOrderByCreatedAtDesc(String clientId);
    long countByClientId(String clientId);
    boolean existsByClientIdAndActionTypeAndCreatedAtAfter(String clientId, String actionType, Instant after);
}
