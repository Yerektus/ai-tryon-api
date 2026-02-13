package io.github.yerektus.aitryon.billing;

import io.github.yerektus.aitryon.billing.dto.BalanceResponse;
import io.github.yerektus.aitryon.billing.dto.CheckoutRequest;
import io.github.yerektus.aitryon.billing.dto.CheckoutResponse;
import io.github.yerektus.aitryon.billing.dto.PaymentPackageResponse;
import io.github.yerektus.aitryon.billing.dto.PaymentStatusResponse;
import io.github.yerektus.aitryon.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping("/packages")
    public List<PaymentPackageResponse> listPackages() {
        return billingService.listPackages();
    }

    @GetMapping("/balance")
    public BalanceResponse getBalance(@AuthenticationPrincipal AuthenticatedUser user) {
        return billingService.getBalance(user.userId());
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public CheckoutResponse checkout(@AuthenticationPrincipal AuthenticatedUser user,
                                     @Valid @RequestBody CheckoutRequest request) {
        return billingService.createCheckout(user.userId(), request);
    }

    @GetMapping("/payments/{paymentId}")
    public PaymentStatusResponse paymentStatus(@AuthenticationPrincipal AuthenticatedUser user,
                                               @PathVariable UUID paymentId) {
        return billingService.getPaymentStatus(user.userId(), paymentId);
    }
}
