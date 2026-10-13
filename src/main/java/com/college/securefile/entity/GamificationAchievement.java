package com.college.securefile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "gamification_achievement",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_client_achievement", columnNames = {"client_id", "achievement_key"})
    },
    indexes = {
        @Index(name = "idx_achievement_client", columnList = "client_id")
    }
)
public class GamificationAchievement {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "client_id", nullable = false, length = 64)
    private String clientId;

    @Column(name = "achievement_key", nullable = false, length = 50)
    private String achievementKey;

    @Column(name = "unlocked_at", nullable = false)
    private Instant unlockedAt;

    public GamificationAchievement() {
        this.id = UUID.randomUUID().toString();
        this.unlockedAt = Instant.now();
    }

    public GamificationAchievement(String clientId, String achievementKey) {
        this();
        this.clientId = clientId;
        this.achievementKey = achievementKey;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getAchievementKey() {
        return achievementKey;
    }

    public void setAchievementKey(String achievementKey) {
        this.achievementKey = achievementKey;
    }

    public Instant getUnlockedAt() {
        return unlockedAt;
    }

    public void setUnlockedAt(Instant unlockedAt) {
        this.unlockedAt = unlockedAt;
    }
}
