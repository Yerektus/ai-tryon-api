package io.github.yerektus.aitryon.billing;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private final BillingService billingService;

    public WebhookController(BillingService billingService) {
        this.billingService = billingService;
    }

    @PostMapping("/stripe")
    @ResponseStatus(HttpStatus.OK)
    public void stripeWebhook(@RequestHeader(name = "Stripe-Signature") String signature,
                              @RequestBody String body) {
        billingService.processStripeWebhook(signature, body);
    }
}
