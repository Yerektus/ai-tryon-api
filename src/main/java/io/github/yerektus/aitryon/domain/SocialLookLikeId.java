package io.github.yerektus.aitryon.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class SocialLookLikeId implements Serializable {

    @Column(name = "look_id", nullable = false)
    private UUID lookId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    public SocialLookLikeId() {
    }

    public SocialLookLikeId(UUID lookId, UUID userId) {
        this.lookId = lookId;
        this.userId = userId;
    }

    public UUID getLookId() {
        return lookId;
    }

    public void setLookId(UUID lookId) {
        this.lookId = lookId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SocialLookLikeId that)) {
            return false;
        }
        return Objects.equals(lookId, that.lookId)
                && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lookId, userId);
    }
}
