package io.github.yerektus.aitryon.billing;

import io.github.yerektus.aitryon.billing.dto.CheckoutRequest;
import io.github.yerektus.aitryon.domain.PaymentEntity;

public interface StripeClient {
    StripeCreateCheckoutSessionResult createCheckoutSession(PaymentEntity payment, CheckoutRequest checkoutRequest);

    StripeStatusResult getCheckoutSessionStatus(String sessionId);
}
