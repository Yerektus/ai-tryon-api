package io.github.yerektus.aitryon.domain.repo;

import io.github.yerektus.aitryon.domain.PaymentPackageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentPackageRepository extends JpaRepository<PaymentPackageEntity, UUID> {
    List<PaymentPackageEntity> findByActiveTrueOrderByAmountMinorAsc();

    Optional<PaymentPackageEntity> findByCodeAndActiveTrue(String code);
}
