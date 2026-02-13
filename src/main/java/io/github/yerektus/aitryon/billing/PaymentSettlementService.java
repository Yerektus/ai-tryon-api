package io.github.yerektus.aitryon.billing;

import io.github.yerektus.aitryon.common.NotFoundException;
import io.github.yerektus.aitryon.domain.CreditLedgerReason;
import io.github.yerektus.aitryon.domain.PaymentEntity;
import io.github.yerektus.aitryon.domain.PaymentStatus;
import io.github.yerektus.aitryon.domain.repo.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentSettlementService {

    private final PaymentRepository paymentRepository;
    private final CreditService creditService;

    public PaymentSettlementService(PaymentRepository paymentRepository, CreditService creditService) {
        this.paymentRepository = paymentRepository;
        this.creditService = creditService;
    }

    @Transactional
    public PaymentEntity applyProviderStatus(UUID paymentId, PaymentStatus status, String providerPayload) {
        final PaymentEntity payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment not found"));

        if (payment.getStatus() == PaymentStatus.PAID) {
            return payment;
        }

        payment.setProviderPayload(providerPayload);

        if (status == PaymentStatus.PAID) {
            payment.setStatus(PaymentStatus.PAID);
            payment.setPaidAt(Instant.now());
            paymentRepository.save(payment);

            creditService.adjustCredits(
                    payment.getUser().getId(),
                    payment.getPaymentPackage().getCredits(),
                    CreditLedgerReason.PAYMENT_TOPUP,
                    payment,
                    null
            );
            return payment;
        }

        if (!payment.getStatus().isTerminal()) {
            payment.setStatus(status);
            paymentRepository.save(payment);
        }

        return payment;
    }
}
