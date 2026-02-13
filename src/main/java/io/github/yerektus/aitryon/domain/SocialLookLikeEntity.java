package io.github.yerektus.aitryon.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "social_look_likes")
public class SocialLookLikeEntity {

    @EmbeddedId
    private SocialLookLikeId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("lookId")
    @JoinColumn(name = "look_id", nullable = false)
    private SocialLookEntity look;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }

        if (id == null && look != null && user != null) {
            id = new SocialLookLikeId(look.getId(), user.getId());
        }
    }

    public SocialLookLikeId getId() {
        return id;
    }

    public void setId(SocialLookLikeId id) {
        this.id = id;
    }

    public SocialLookEntity getLook() {
        return look;
    }

    public void setLook(SocialLookEntity look) {
        this.look = look;
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
