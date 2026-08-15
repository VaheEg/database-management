package org.example.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {

    private static final int MIN_SECRET_LENGTH = 32;

    private static final String BEARER_PREFIX = "Bearer ";

    static final String CLAIM_ROLES = "roles";

    static final String CLAIM_USER_ID = "uid";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        String secret = properties.getSecret();
        if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "app.jwt.secret must be set and at least " + MIN_SECRET_LENGTH + " characters long");
        }
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public record IssuedToken(String token, String id, Instant issuedAt, Instant expiresAt) {
    }

    public IssuedToken issue(Long userId, String username, List<String> roles) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.getExpiration());
        String id = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .id(id)
                .issuer(properties.getIssuer())
                .subject(username)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_ROLES, roles)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
        return new IssuedToken(token, id, issuedAt, expiresAt);
    }

    public Duration expiration() {
        return properties.getExpiration();
    }

    public Optional<Claims> parse(String token) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public boolean isValid(String token) {
        return parse(token).isPresent();
    }

    public Optional<String> extractUsername(String token) {
        return parse(token).map(Claims::getSubject);
    }

    public static Optional<Instant> expiresAt(Claims claims) {
        return Optional.ofNullable(claims.getExpiration()).map(Date::toInstant);
    }

    public static Optional<String> stripBearer(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }
}
