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

/**
 * Issues and validates the access tokens used to authenticate API calls.
 */
@Service
public class JwtService {

    private static final int MIN_SECRET_LENGTH = 32;

    /** Roles are carried in the token so the filter can build authorities without a DB hit. */
    static final String CLAIM_ROLES = "roles";

    /** The user's id, so the caller can be resolved without a lookup by username. */
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

    /** Issues a token whose subject is the username and which carries the user's id and roles. */
    public String generateToken(Long userId, String username, List<String> roles) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getExpiration());
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(username)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_ROLES, roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    public Instant expiresAt(Instant issuedAt) {
        return issuedAt.plus(properties.getExpiration());
    }

    public Duration expiration() {
        return properties.getExpiration();
    }

    /**
     * Parses and verifies a token.
     *
     * @return the claims, or empty if the token is malformed, expired, or not signed by us
     */
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
}
