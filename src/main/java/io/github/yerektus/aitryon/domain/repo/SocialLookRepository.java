package io.github.yerektus.aitryon.domain.repo;

import io.github.yerektus.aitryon.domain.SocialLookEntity;
import io.github.yerektus.aitryon.domain.SocialLookVisibility;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SocialLookRepository extends JpaRepository<SocialLookEntity, UUID> {
    long countByAuthor_Id(UUID authorId);

    List<SocialLookEntity> findByAuthor_IdOrderByCreatedAtDescIdDesc(UUID authorId, Pageable pageable);

    List<SocialLookEntity> findByAuthor_IdAndVisibilityInOrderByCreatedAtDescIdDesc(
            UUID authorId,
            Collection<SocialLookVisibility> visibility,
            Pageable pageable
    );

    List<SocialLookEntity> findByVisibilityOrderByCreatedAtDescIdDesc(
            SocialLookVisibility visibility,
            Pageable pageable
    );
}
