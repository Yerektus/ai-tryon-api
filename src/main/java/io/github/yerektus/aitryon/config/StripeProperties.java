package io.github.yerektus.aitryon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.stripe")
public class StripeProperties {
    private String baseUrl;
    private String secretKey;
    private String webhookSecret;
    private long signatureToleranceSeconds = 300;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public long getSignatureToleranceSeconds() {
        return signatureToleranceSeconds;
    }

    public void setSignatureToleranceSeconds(long signatureToleranceSeconds) {
        this.signatureToleranceSeconds = signatureToleranceSeconds;
    }
}
