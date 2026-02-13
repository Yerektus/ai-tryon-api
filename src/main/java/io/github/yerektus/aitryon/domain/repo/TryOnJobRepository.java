package io.github.yerektus.aitryon.domain.repo;

import io.github.yerektus.aitryon.domain.TryOnJobEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TryOnJobRepository extends JpaRepository<TryOnJobEntity, UUID> {
    List<TryOnJobEntity> findByUser_IdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<TryOnJobEntity> findByIdAndUser_Id(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from TryOnJobEntity j where j.id = :id")
    Optional<TryOnJobEntity> findByIdForUpdate(@Param("id") UUID id);
}
