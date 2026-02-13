package io.github.yerektus.aitryon.domain.repo;

import io.github.yerektus.aitryon.domain.SocialLookDraftEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SocialLookDraftRepository extends JpaRepository<SocialLookDraftEntity, UUID> {
}
