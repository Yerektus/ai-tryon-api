package io.github.yerektus.aitryon.domain.repo;

import io.github.yerektus.aitryon.domain.PaymentWebhookEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEventEntity, UUID> {
    boolean existsByProviderEventId(String providerEventId);
}
