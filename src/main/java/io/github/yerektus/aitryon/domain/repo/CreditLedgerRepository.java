package io.github.yerektus.aitryon.domain.repo;

import io.github.yerektus.aitryon.domain.CreditLedgerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CreditLedgerRepository extends JpaRepository<CreditLedgerEntity, UUID> {
}
