package io.github.yerektus.aitryon.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "try_on_jobs")
public class TryOnJobEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "person_image", nullable = false, columnDefinition = "BYTEA")
    private byte[] personImage;

    @Column(name = "person_image_mime", nullable = false, length = 64)
    private String personImageMime;

    @Column(name = "clothing_image", nullable = false, columnDefinition = "BYTEA")
    private byte[] clothingImage;

    @Column(name = "clothing_image_mime", nullable = false, length = 64)
    private String clothingImageMime;

    @Column(name = "result_image", columnDefinition = "BYTEA")
    private byte[] resultImage;

    @Column(name = "result_image_mime", length = 64)
    private String resultImageMime;

    @Column(name = "clothing_name", nullable = false, length = 120)
    private String clothingName;

    @Column(name = "clothing_size", nullable = false, length = 32)
    private String clothingSize;

    @Column(name = "height_cm", nullable = false)
    private int heightCm;

    @Column(name = "weight_kg", nullable = false)
    private int weightKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 16)
    private UserGender gender;

    @Column(name = "age_years", nullable = false)
    private int ageYears;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TryOnJobStatus status;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "credits_spent", nullable = false)
    private int creditsSpent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        final Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
    }

    public byte[] getPersonImage() {
        return personImage;
    }

    public void setPersonImage(byte[] personImage) {
        this.personImage = personImage;
    }

    public String getPersonImageMime() {
        return personImageMime;
    }

    public void setPersonImageMime(String personImageMime) {
        this.personImageMime = personImageMime;
    }

    public byte[] getClothingImage() {
        return clothingImage;
    }

    public void setClothingImage(byte[] clothingImage) {
        this.clothingImage = clothingImage;
    }

    public String getClothingImageMime() {
        return clothingImageMime;
    }

    public void setClothingImageMime(String clothingImageMime) {
        this.clothingImageMime = clothingImageMime;
    }

    public byte[] getResultImage() {
        return resultImage;
    }

    public void setResultImage(byte[] resultImage) {
        this.resultImage = resultImage;
    }

    public String getResultImageMime() {
        return resultImageMime;
    }

    public void setResultImageMime(String resultImageMime) {
        this.resultImageMime = resultImageMime;
    }

    public String getClothingName() {
        return clothingName;
    }

    public void setClothingName(String clothingName) {
        this.clothingName = clothingName;
    }

    public String getClothingSize() {
        return clothingSize;
    }

    public void setClothingSize(String clothingSize) {
        this.clothingSize = clothingSize;
    }

    public int getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(int heightCm) {
        this.heightCm = heightCm;
    }

    public int getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(int weightKg) {
        this.weightKg = weightKg;
    }

    public UserGender getGender() {
        return gender;
    }

    public void setGender(UserGender gender) {
        this.gender = gender;
    }

    public int getAgeYears() {
        return ageYears;
    }

    public void setAgeYears(int ageYears) {
        this.ageYears = ageYears;
    }

    public TryOnJobStatus getStatus() {
        return status;
    }

    public void setStatus(TryOnJobStatus status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public int getCreditsSpent() {
        return creditsSpent;
    }

    public void setCreditsSpent(int creditsSpent) {
        this.creditsSpent = creditsSpent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
