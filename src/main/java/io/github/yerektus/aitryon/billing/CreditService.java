package io.github.yerektus.aitryon.billing;

import io.github.yerektus.aitryon.common.NotFoundException;
import io.github.yerektus.aitryon.common.PaymentRequiredException;
import io.github.yerektus.aitryon.domain.CreditLedgerEntity;
import io.github.yerektus.aitryon.domain.CreditLedgerReason;
import io.github.yerektus.aitryon.domain.PaymentEntity;
import io.github.yerektus.aitryon.domain.TryOnJobEntity;
import io.github.yerektus.aitryon.domain.UserEntity;
import io.github.yerektus.aitryon.domain.repo.CreditLedgerRepository;
import io.github.yerektus.aitryon.domain.repo.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CreditService {

    private final UserRepository userRepository;
    private final CreditLedgerRepository creditLedgerRepository;

    public CreditService(UserRepository userRepository, CreditLedgerRepository creditLedgerRepository) {
        this.userRepository = userRepository;
        this.creditLedgerRepository = creditLedgerRepository;
    }

    @Transactional
    public int adjustCredits(UUID userId,
                             int delta,
                             CreditLedgerReason reason,
                             PaymentEntity payment,
                             TryOnJobEntity tryOnJob) {
        final UserEntity user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        final int nextBalance = user.getCreditsBalance() + delta;
        if (nextBalance < 0) {
            throw new PaymentRequiredException("Not enough credits");
        }

        user.setCreditsBalance(nextBalance);
        userRepository.save(user);

        final CreditLedgerEntity ledger = new CreditLedgerEntity();
        ledger.setUser(user);
        ledger.setDelta(delta);
        ledger.setBalanceAfter(nextBalance);
        ledger.setReason(reason);
        ledger.setPayment(payment);
        ledger.setTryOnJob(tryOnJob);
        creditLedgerRepository.save(ledger);

        return nextBalance;
    }

    @Transactional(readOnly = true)
    public int getBalance(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"))
                .getCreditsBalance();
    }
}
