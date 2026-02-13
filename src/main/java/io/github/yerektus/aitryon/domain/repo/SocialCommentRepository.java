package io.github.yerektus.aitryon.domain.repo;

import io.github.yerektus.aitryon.domain.SocialCommentEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SocialCommentRepository extends JpaRepository<SocialCommentEntity, UUID> {
    List<SocialCommentEntity> findByLook_IdAndParentIsNullOrderByCreatedAtDescIdDesc(UUID lookId, Pageable pageable);

    List<SocialCommentEntity> findByLook_IdAndParent_IdOrderByCreatedAtDescIdDesc(UUID lookId, UUID parentId, Pageable pageable);

    long countByLook_Id(UUID lookId);

    long countByParent_Id(UUID parentId);

    void deleteByParent_Id(UUID parentId);
}
