package com.college.securefile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "gamification_activity",
    indexes = {
        @Index(name = "idx_activity_client", columnList = "client_id"),
        @Index(name = "idx_activity_action_key", columnList = "action_key", unique = true)
    }
)
public class GamificationActivity {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "client_id", nullable = false, length = 64)
    private String clientId;

    @Column(name = "action_key", nullable = false, unique = true, length = 120)
    private String actionKey;

    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Column(name = "xp_earned", nullable = false)
    private int xpEarned;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public GamificationActivity() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
    }

    public GamificationActivity(String clientId, String actionKey, String actionType,
                                String description, int xpEarned) {
        this();
        this.clientId = clientId;
        this.actionKey = actionKey;
        this.actionType = actionType;
        this.description = description;
        this.xpEarned = xpEarned;
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

    public String getActionKey() {
        return actionKey;
    }

    public void setActionKey(String actionKey) {
        this.actionKey = actionKey;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getXpEarned() {
        return xpEarned;
    }

    public void setXpEarned(int xpEarned) {
        this.xpEarned = xpEarned;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
