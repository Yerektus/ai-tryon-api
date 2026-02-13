package io.github.yerektus.aitryon.domain.repo;

import io.github.yerektus.aitryon.domain.SocialLookLikeEntity;
import io.github.yerektus.aitryon.domain.SocialLookLikeId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SocialLookLikeRepository extends JpaRepository<SocialLookLikeEntity, SocialLookLikeId> {
    boolean existsByLook_IdAndUser_Id(UUID lookId, UUID userId);

    long countByLook_Id(UUID lookId);

    void deleteByLook_IdAndUser_Id(UUID lookId, UUID userId);
}
