package io.github.yerektus.aitryon.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yerektus.aitryon.billing.dto.BalanceResponse;
import io.github.yerektus.aitryon.billing.dto.CheckoutRequest;
import io.github.yerektus.aitryon.billing.dto.CheckoutResponse;
import io.github.yerektus.aitryon.billing.dto.PaymentPackageResponse;
import io.github.yerektus.aitryon.billing.dto.PaymentStatusResponse;
import io.github.yerektus.aitryon.common.BadRequestException;
import io.github.yerektus.aitryon.common.NotFoundException;
import io.github.yerektus.aitryon.config.StripeProperties;
import io.github.yerektus.aitryon.domain.PaymentEntity;
import io.github.yerektus.aitryon.domain.PaymentPackageEntity;
import io.github.yerektus.aitryon.domain.PaymentProvider;
import io.github.yerektus.aitryon.domain.PaymentStatus;
import io.github.yerektus.aitryon.domain.PaymentWebhookEventEntity;
import io.github.yerektus.aitryon.domain.UserEntity;
import io.github.yerektus.aitryon.domain.repo.PaymentPackageRepository;
import io.github.yerektus.aitryon.domain.repo.PaymentRepository;
import io.github.yerektus.aitryon.domain.repo.PaymentWebhookEventRepository;
import io.github.yerektus.aitryon.domain.repo.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class BillingService {

    private final PaymentPackageRepository paymentPackageRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentWebhookEventRepository webhookEventRepository;
    private final UserRepository userRepository;
    private final CreditService creditService;
    private final StripeClient stripeClient;
    private final PaymentSettlementService paymentSettlementService;
    private final StripeProperties stripeProperties;
    private final ObjectMapper objectMapper;

    public BillingService(PaymentPackageRepository paymentPackageRepository,
                          PaymentRepository paymentRepository,
                          PaymentWebhookEventRepository webhookEventRepository,
                          UserRepository userRepository,
                          CreditService creditService,
                          StripeClient stripeClient,
                          PaymentSettlementService paymentSettlementService,
                          StripeProperties stripeProperties,
                          ObjectMapper objectMapper) {
        this.paymentPackageRepository = paymentPackageRepository;
        this.paymentRepository = paymentRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.userRepository = userRepository;
        this.creditService = creditService;
        this.stripeClient = stripeClient;
        this.paymentSettlementService = paymentSettlementService;
        this.stripeProperties = stripeProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<PaymentPackageResponse> listPackages() {
        return paymentPackageRepository.findByActiveTrueOrderByAmountMinorAsc().stream()
                .map(this::toPackageResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(UUID userId) {
        return new BalanceResponse(creditService.getBalance(userId));
    }

    @Transactional
    public CheckoutResponse createCheckout(UUID userId, CheckoutRequest request) {
        final UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        final PaymentPackageEntity paymentPackage = paymentPackageRepository
                .findByCodeAndActiveTrue(request.packageCode())
                .orElseThrow(() -> new NotFoundException("Payment package not found"));

        final PaymentEntity payment = new PaymentEntity();
        payment.setUser(user);
        payment.setPaymentPackage(paymentPackage);
        payment.setProvider(PaymentProvider.STRIPE);
        payment.setAmountMinor(paymentPackage.getAmountMinor());
        payment.setCurrency(paymentPackage.getCurrency());
        payment.setStatus(PaymentStatus.CREATED);
        final PaymentEntity saved = paymentRepository.save(payment);

        final StripeCreateCheckoutSessionResult stripeSession = stripeClient.createCheckoutSession(saved, request);
        saved.setProviderInvoiceId(stripeSession.sessionId());
        saved.setRedirectUrl(stripeSession.redirectUrl());
        saved.setProviderPayload(stripeSession.rawPayload());
        saved.setStatus(stripeSession.status() == PaymentStatus.CREATED ? PaymentStatus.PENDING : stripeSession.status());
        final PaymentEntity updated = paymentRepository.save(saved);

        return new CheckoutResponse(
                updated.getId(),
                updated.getProvider().name(),
                updated.getRedirectUrl(),
                stripeSession.expiresAt()
        );
    }

    @Transactional
    public PaymentStatusResponse getPaymentStatus(UUID userId, UUID paymentId) {
        final PaymentEntity payment = paymentRepository.findByIdAndUser_Id(paymentId, userId)
                .orElseThrow(() -> new NotFoundException("Payment not found"));

        if (!payment.getStatus().isTerminal()
                && payment.getProvider() == PaymentProvider.STRIPE
                && payment.getProviderInvoiceId() != null) {
            final StripeStatusResult statusResult = stripeClient.getCheckoutSessionStatus(payment.getProviderInvoiceId());
            paymentSettlementService.applyProviderStatus(payment.getId(), statusResult.status(), statusResult.rawPayload());
        }

        final PaymentEntity refreshed = paymentRepository.findByIdAndUser_Id(paymentId, userId)
                .orElseThrow(() -> new NotFoundException("Payment not found"));
        return toStatusResponse(refreshed);
    }

    @Transactional
    public void processStripeWebhook(String signatureHeader, String rawBody) {
        if (!verifyStripeSignature(signatureHeader, rawBody)) {
            throw new BadRequestException("Invalid webhook signature");
        }

        final JsonNode payload;
        try {
            payload = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            throw new BadRequestException("Webhook payload is not valid JSON");
        }

        final String eventId = resolveStripeEventId(payload, rawBody);
        if (webhookEventRepository.existsByProviderEventId(eventId)) {
            return;
        }

        final PaymentWebhookEventEntity event = new PaymentWebhookEventEntity();
        event.setProvider(PaymentProvider.STRIPE);
        event.setProviderEventId(eventId);
        event.setPayload(rawBody);
        webhookEventRepository.save(event);

        final JsonNode eventObject = payload.path("data").path("object");
        final String sessionId = text(eventObject, "id");
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        PaymentEntity payment = paymentRepository.findByProviderInvoiceId(sessionId).orElse(null);
        if (payment == null) {
            final String metadataPaymentId = text(eventObject.path("metadata"), "paymentId");
            if (metadataPaymentId != null && !metadataPaymentId.isBlank()) {
                try {
                    payment = paymentRepository.findById(UUID.fromString(metadataPaymentId)).orElse(null);
                } catch (IllegalArgumentException ignored) {
                    payment = null;
                }
            }
        }
        if (payment == null) {
            return;
        }

        final String eventType = text(payload, "type");
        final PaymentStatus status = mapStripeWebhookStatus(eventType, eventObject);
        paymentSettlementService.applyProviderStatus(payment.getId(), status, rawBody);
    }

    private PaymentPackageResponse toPackageResponse(PaymentPackageEntity pkg) {
        return new PaymentPackageResponse(
                pkg.getCode(),
                pkg.getTitle(),
                pkg.getCredits(),
                pkg.getAmountMinor(),
                pkg.getCurrency()
        );
    }

    private PaymentStatusResponse toStatusResponse(PaymentEntity payment) {
        return new PaymentStatusResponse(
                payment.getId(),
                payment.getProvider().name(),
                payment.getProviderInvoiceId(),
                payment.getStatus().name(),
                payment.getAmountMinor(),
                payment.getCurrency(),
                payment.getPaymentPackage().getCredits(),
                payment.getRedirectUrl(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }

    private boolean verifyStripeSignature(String signatureHeader, String payload) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }

        final String secret = stripeProperties.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            throw new BadRequestException("STRIPE_WEBHOOK_SECRET is not configured");
        }

        final ParsedStripeSignature parsed = parseStripeSignature(signatureHeader);
        if (parsed.timestamp() == null || parsed.signatures().isEmpty()) {
            return false;
        }

        final long now = Instant.now().getEpochSecond();
        final long toleranceSeconds = Math.max(30L, stripeProperties.getSignatureToleranceSeconds());
        if (Math.abs(now - parsed.timestamp()) > toleranceSeconds) {
            return false;
        }

        final String signedPayload = parsed.timestamp() + "." + payload;
        final String expected = hmacSha256(secret, signedPayload);
        final byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);

        for (String signature : parsed.signatures()) {
            if (MessageDigest.isEqual(expectedBytes, signature.getBytes(StandardCharsets.UTF_8))) {
                return true;
            }
        }
        return false;
    }

    private ParsedStripeSignature parseStripeSignature(String signatureHeader) {
        Long timestamp = null;
        List<String> signatures = new ArrayList<>();

        for (String token : signatureHeader.split(",")) {
            final String part = token.trim();
            final int delimiter = part.indexOf('=');
            if (delimiter <= 0 || delimiter == part.length() - 1) {
                continue;
            }

            final String key = part.substring(0, delimiter).trim();
            final String value = part.substring(delimiter + 1).trim().toLowerCase(Locale.ROOT);
            if ("t".equals(key)) {
                try {
                    timestamp = Long.parseLong(value);
                } catch (NumberFormatException ignored) {
                    return new ParsedStripeSignature(null, List.of());
                }
            } else if ("v1".equals(key) && !value.isBlank()) {
                signatures.add(value);
            }
        }

        return new ParsedStripeSignature(timestamp, signatures);
    }

    private String hmacSha256(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)))
                    .toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to verify webhook signature", e);
        }
    }

    private String resolveStripeEventId(JsonNode payload, String rawBody) {
        final String payloadEventId = text(payload, "id");
        if (payloadEventId != null && !payloadEventId.isBlank()) {
            return payloadEventId;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawBody.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private String text(JsonNode node, String... fields) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        for (String field : fields) {
            if (node.hasNonNull(field)) {
                final String value = node.get(field).asText(null);
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
            if (node.has("data") && node.get("data").hasNonNull(field)) {
                final String value = node.get("data").get(field).asText(null);
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    private PaymentStatus mapStripeWebhookStatus(String eventTypeRaw, JsonNode eventObject) {
        final String eventType = eventTypeRaw == null ? "" : eventTypeRaw.trim().toLowerCase(Locale.ROOT);
        final String sessionStatus = text(eventObject, "status");
        final String paymentStatus = text(eventObject, "payment_status");

        return switch (eventType) {
            case "checkout.session.expired" -> PaymentStatus.EXPIRED;
            case "checkout.session.async_payment_failed" -> PaymentStatus.FAILED;
            case "checkout.session.completed", "checkout.session.async_payment_succeeded" -> mapStripeSessionStatus(sessionStatus, paymentStatus);
            default -> mapStripeSessionStatus(sessionStatus, paymentStatus);
        };
    }

    private PaymentStatus mapStripeSessionStatus(String sessionStatusRaw, String paymentStatusRaw) {
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

    private record ParsedStripeSignature(Long timestamp, List<String> signatures) {
    }
}
