package io.github.yerektus.aitryon.auth;

import io.github.yerektus.aitryon.auth.dto.AuthResponse;
import io.github.yerektus.aitryon.auth.dto.GoogleAuthRequest;
import io.github.yerektus.aitryon.auth.dto.LoginRequest;
import io.github.yerektus.aitryon.auth.dto.RefreshRequest;
import io.github.yerektus.aitryon.auth.dto.RegisterRequest;
import io.github.yerektus.aitryon.auth.dto.UserResponse;
import io.github.yerektus.aitryon.billing.CreditService;
import io.github.yerektus.aitryon.common.ConflictException;
import io.github.yerektus.aitryon.common.UnauthorizedException;
import io.github.yerektus.aitryon.domain.CreditLedgerReason;
import io.github.yerektus.aitryon.domain.RefreshTokenEntity;
import io.github.yerektus.aitryon.domain.UserEntity;
import io.github.yerektus.aitryon.domain.repo.RefreshTokenRepository;
import io.github.yerektus.aitryon.domain.repo.UserRepository;
import io.github.yerektus.aitryon.security.AuthenticatedUser;
import io.github.yerektus.aitryon.security.JwtService;
import io.github.yerektus.aitryon.security.TokenHashService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenHashService tokenHashService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final CreditService creditService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       TokenHashService tokenHashService,
                       GoogleTokenVerifier googleTokenVerifier,
                       CreditService creditService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenHashService = tokenHashService;
        this.googleTokenVerifier = googleTokenVerifier;
        this.creditService = creditService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        final String email = normalizeEmail(request.email());
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new ConflictException("Email is already registered");
        }

        final UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setDisplayName(request.displayName().trim());
        user.setUsername(generateUniqueUsername(request.displayName()));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setCreditsBalance(0);
        final UserEntity saved = userRepository.save(user);

        creditService.adjustCredits(saved.getId(), 5, CreditLedgerReason.WELCOME_BONUS, null, null);
        final UserEntity refreshed = userRepository.findById(saved.getId()).orElseThrow();
        return issueTokens(refreshed);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        final String email = normalizeEmail(request.email());
        final UserEntity user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse loginWithGoogle(GoogleAuthRequest request) {
        final GoogleIdentity identity = googleTokenVerifier.verify(request.idToken());
        final String normalizedEmail = normalizeEmail(identity.email());

        UserEntity user = userRepository.findByGoogleSub(identity.sub()).orElse(null);
        if (user == null) {
            user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
            if (user == null) {
                user = new UserEntity();
                user.setEmail(normalizedEmail);
                user.setDisplayName(identity.displayName() == null || identity.displayName().isBlank()
                        ? normalizedEmail
                        : identity.displayName().trim());
                user.setUsername(generateUniqueUsername(user.getDisplayName()));
                user.setGoogleSub(identity.sub());
                user.setCreditsBalance(0);
                user = userRepository.save(user);
                creditService.adjustCredits(user.getId(), 5, CreditLedgerReason.WELCOME_BONUS, null, null);
                user = userRepository.findById(user.getId()).orElseThrow();
            } else {
                user.setGoogleSub(identity.sub());
                if (user.getDisplayName() == null || user.getDisplayName().isBlank()) {
                    user.setDisplayName(identity.displayName());
                }
                if (user.getUsername() == null || user.getUsername().isBlank()) {
                    user.setUsername(generateUniqueUsername(user.getDisplayName()));
                }
                user = userRepository.save(user);
            }
        }

        if (user.getUsername() == null || user.getUsername().isBlank()) {
            user.setUsername(generateUniqueUsername(user.getDisplayName()));
            user = userRepository.save(user);
        }

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        final String refreshToken = request.refreshToken().trim();
        final String hashed = tokenHashService.hash(refreshToken);

        final RefreshTokenEntity tokenEntity = refreshTokenRepository.findByTokenHash(hashed)
                .orElseThrow(() -> new UnauthorizedException("Refresh token is invalid"));

        if (tokenEntity.getRevokedAt() != null || tokenEntity.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Refresh token is expired");
        }

        tokenEntity.setRevokedAt(Instant.now());
        refreshTokenRepository.save(tokenEntity);

        return issueTokens(tokenEntity.getUser());
    }

    @Transactional
    public void logout(String refreshToken) {
        final String hashed = tokenHashService.hash(refreshToken.trim());
        refreshTokenRepository.findByTokenHash(hashed).ifPresent(token -> {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
        });
    }

    public UserResponse me(AuthenticatedUser authenticatedUser) {
        final UserEntity user = userRepository.findById(authenticatedUser.userId())
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        return toUserResponse(user);
    }

    private AuthResponse issueTokens(UserEntity user) {
        final String accessToken = jwtService.createAccessToken(user);
        final String refreshToken = generateRefreshToken();

        final RefreshTokenEntity tokenEntity = new RefreshTokenEntity();
        tokenEntity.setUser(user);
        tokenEntity.setTokenHash(tokenHashService.hash(refreshToken));
        tokenEntity.setExpiresAt(Instant.now().plus(jwtService.getRefreshTtlDays(), ChronoUnit.DAYS));
        refreshTokenRepository.save(tokenEntity);

        return new AuthResponse(
                toUserResponse(user),
                accessToken,
                refreshToken,
                jwtService.getAccessTtlSeconds()
        );
    }

    private UserResponse toUserResponse(UserEntity user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getCreditsBalance()
        );
    }

    private String generateRefreshToken() {
        final byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String generateUniqueUsername(String seed) {
        String base = normalizeUsernameSeed(seed);
        if (base.isBlank()) {
            base = "user";
        }

        if (!userRepository.existsByUsername(base)) {
            return base;
        }

        for (int attempt = 0; attempt < 1000; attempt++) {
            final int suffix = secureRandom.nextInt(1_000_000);
            final String candidate = limitUsername(base, 64 - 7) + "_" + String.format("%06d", suffix);
            if (!userRepository.existsByUsername(candidate)) {
                return candidate;
            }
        }

        final String fallback = "user_" + generateRefreshToken().substring(0, 16).toLowerCase(Locale.ROOT);
        return fallback.length() > 64 ? fallback.substring(0, 64) : fallback;
    }

    private String normalizeUsernameSeed(String seed) {
        if (seed == null) {
            return "";
        }

        String normalized = seed.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9_]+", "_");
        normalized = normalized.replaceAll("_+", "_");
        normalized = normalized.replaceAll("^_+|_+$", "");
        return limitUsername(normalized, 64);
    }

    private String limitUsername(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
