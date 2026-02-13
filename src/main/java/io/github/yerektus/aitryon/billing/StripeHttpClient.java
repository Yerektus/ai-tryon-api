package io.github.yerektus.aitryon.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yerektus.aitryon.billing.dto.CheckoutRequest;
import io.github.yerektus.aitryon.common.BadRequestException;
import io.github.yerektus.aitryon.common.ExternalServiceException;
import io.github.yerektus.aitryon.config.StripeProperties;
import io.github.yerektus.aitryon.domain.PaymentEntity;
import io.github.yerektus.aitryon.domain.PaymentStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class StripeHttpClient implements StripeClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final StripeProperties stripeProperties;

    public StripeHttpClient(HttpClient httpClient, ObjectMapper objectMapper, StripeProperties stripeProperties) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.stripeProperties = stripeProperties;
    }

    @Override
    public StripeCreateCheckoutSessionResult createCheckoutSession(PaymentEntity payment, CheckoutRequest checkoutRequest) {
        final String secretKey = stripeSecretKey();

        final List<String[]> fields = new ArrayList<>();
        fields.add(pair("mode", "payment"));
        fields.add(pair("success_url", checkoutRequest.successUrl()));
        fields.add(pair("cancel_url", checkoutRequest.cancelUrl()));
        fields.add(pair("client_reference_id", payment.getId().toString()));
        fields.add(pair("metadata[paymentId]", payment.getId().toString()));
        fields.add(pair("metadata[userId]", payment.getUser().getId().toString()));
        fields.add(pair("metadata[packageCode]", payment.getPaymentPackage().getCode()));
        fields.add(pair("payment_method_types[0]", "card"));
        fields.add(pair("line_items[0][quantity]", "1"));
        fields.add(pair("line_items[0][price_data][currency]", payment.getCurrency().toLowerCase(Locale.ROOT)));
        fields.add(pair("line_items[0][price_data][unit_amount]", String.valueOf(payment.getAmountMinor())));
        fields.add(pair("line_items[0][price_data][product_data][name]", payment.getPaymentPackage().getTitle()));
        fields.add(pair("line_items[0][price_data][product_data][description]", "+" + payment.getPaymentPackage().getCredits() + " credits"));

        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(trimBase(stripeProperties.getBaseUrl()) + "/checkout/sessions"))
                .header("Authorization", "Bearer " + secretKey)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formEncode(fields)))
                .build();

        try {
            final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ExternalServiceException("Stripe create checkout session failed: HTTP " + response.statusCode());
            }

            final JsonNode root = objectMapper.readTree(response.body());
            final String sessionId = text(root, "id");
            final String redirectUrl = text(root, "url");
            final String statusRaw = text(root, "status");
            final String paymentStatusRaw = text(root, "payment_status");
            final Instant expiresAt = parseUnixTimestamp(root.path("expires_at").asLong(0L));

            if (sessionId == null || sessionId.isBlank() || redirectUrl == null || redirectUrl.isBlank()) {
                throw new ExternalServiceException("Stripe create checkout session response is invalid");
            }

            return new StripeCreateCheckoutSessionResult(
                    sessionId,
                    redirectUrl,
                    mapSessionStatus(statusRaw, paymentStatusRaw),
                    expiresAt,
                    response.body()
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException("Stripe request interrupted");
        } catch (IOException e) {
            throw new ExternalServiceException("Stripe response parsing failed");
        }
    }

    @Override
    public StripeStatusResult getCheckoutSessionStatus(String sessionId) {
        final String secretKey = stripeSecretKey();

        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(trimBase(stripeProperties.getBaseUrl()) + "/checkout/sessions/" + urlEncode(sessionId)))
                .header("Authorization", "Bearer " + secretKey)
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ExternalServiceException("Stripe status check failed: HTTP " + response.statusCode());
            }

            final JsonNode root = objectMapper.readTree(response.body());
            final String statusRaw = text(root, "status");
            final String paymentStatusRaw = text(root, "payment_status");
            return new StripeStatusResult(sessionId, mapSessionStatus(statusRaw, paymentStatusRaw), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException("Stripe request interrupted");
        } catch (IOException e) {
            throw new ExternalServiceException("Stripe response parsing failed");
        }
    }

    private String stripeSecretKey() {
        if (stripeProperties.getSecretKey() == null || stripeProperties.getSecretKey().isBlank()) {
            throw new BadRequestException("STRIPE_SECRET_KEY is not configured");
        }
        return stripeProperties.getSecretKey();
    }

    private String trimBase(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BadRequestException("STRIPE_BASE_URL is not configured");
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private String[] pair(String key, String value) {
        return new String[]{key, value};
    }

    private String formEncode(List<String[]> fields) {
        final StringBuilder builder = new StringBuilder();
        for (int index = 0; index < fields.size(); index++) {
            final String[] field = fields.get(index);
            if (index > 0) {
                builder.append('&');
            }
            builder.append(urlEncode(field[0]))
                    .append('=')
                    .append(urlEncode(field[1]));
        }
        return builder.toString();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String text(JsonNode node, String field) {
        if (node.hasNonNull(field)) {
            final String value = node.get(field).asText(null);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private Instant parseUnixTimestamp(long timestamp) {
        if (timestamp <= 0) {
            return Instant.now().plusSeconds(30 * 60);
        }
        return Instant.ofEpochSecond(timestamp);
    }

    private PaymentStatus mapSessionStatus(String sessionStatusRaw, String paymentStatusRaw) {
        final String sessionStatus = sessionStatusRaw == null ? "" : sessionStatusRaw.trim().toLowerCase(Locale.ROOT);
        final String paymentStatus = paymentStatusRaw == null ? "" : paymentStatusRaw.trim().toLowerCase(Locale.ROOT);

        if ("paid".equals(paymentStatus)) {
            return PaymentStatus.PAID;
        }
        if ("canceled".equals(sessionStatus) || "cancelled".equals(sessionStatus)) {
            return PaymentStatus.CANCELED;
        }
        if ("expired".equals(sessionStatus)) {
            return PaymentStatus.EXPIRED;
        }
        if ("complete".equals(sessionStatus) && "unpaid".equals(paymentStatus)) {
            return PaymentStatus.FAILED;
        }
        if ("failed".equals(paymentStatus)) {
            return PaymentStatus.FAILED;
        }
        return PaymentStatus.PENDING;
    }
}
