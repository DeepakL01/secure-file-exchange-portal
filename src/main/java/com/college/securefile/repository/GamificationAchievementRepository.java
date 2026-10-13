package com.college.securefile.repository;

import com.college.securefile.entity.GamificationAchievement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GamificationAchievementRepository extends JpaRepository<GamificationAchievement, String> {
    List<GamificationAchievement> findByClientId(String clientId);
    boolean existsByClientIdAndAchievementKey(String clientId, String achievementKey);
    Optional<GamificationAchievement> findByClientIdAndAchievementKey(String clientId, String achievementKey);
}
