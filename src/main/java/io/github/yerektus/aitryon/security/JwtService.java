package io.github.yerektus.aitryon.security;

import io.github.yerektus.aitryon.config.JwtProperties;
import io.github.yerektus.aitryon.domain.UserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {
    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(UserEntity user) {
        final Instant now = Instant.now();
        final Instant exp = now.plus(jwtProperties.getAccessTtlMinutes(), ChronoUnit.MINUTES);

        return Jwts.builder()
                .subject(user.getId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claims(Map.of("email", user.getEmail(), "type", "access"))
                .signWith(secretKey)
                .compact();
    }

    public Instant accessTokenExpiryInstant() {
        return Instant.now().plus(jwtProperties.getAccessTtlMinutes(), ChronoUnit.MINUTES);
    }

    public long getAccessTtlSeconds() {
        return jwtProperties.getAccessTtlMinutes() * 60;
    }

    public Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID userIdFromClaims(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    public String emailFromClaims(Claims claims) {
        return claims.get("email", String.class);
    }

    public long getRefreshTtlDays() {
        return jwtProperties.getRefreshTtlDays();
    }
}
