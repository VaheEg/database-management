package org.example.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers the tokens that were logged out, keyed by their {@code jti} claim.
 *
 * <p>A JWT is self-contained: the server can verify it without any stored state,
 * which is what makes the API stateless — and also what makes "log out" mean
 * nothing on its own. This store is the small piece of state that gives logout
 * teeth: {@link JwtAuthFilter} refuses any token whose id is listed here.
 *
 * <p>Entries are dropped once the token would have expired anyway, so the map
 * stays bounded by the number of logouts inside one {@code app.jwt.expiration}
 * window. It lives in memory, which has two consequences worth knowing:
 * a restart forgets the revocations (tokens issued before it become usable again
 * until they expire), and a multi-instance deployment would need a shared store
 * such as Redis or a small database table instead.
 */
@Component
@Slf4j
public class RevokedTokenStore {

    /** Prune when the map has grown past this, so a long-lived process cannot creep upwards. */
    private static final int PRUNE_THRESHOLD = 1_000;

    private final Map<String, Instant> revokedUntil = new ConcurrentHashMap<>();

    /**
     * Marks a token as no longer usable.
     *
     * @param tokenId  the token's {@code jti}
     * @param expiresAt when the token expires on its own; after this the entry is pointless
     */
    public void revoke(String tokenId, Instant expiresAt) {
        if (tokenId == null || expiresAt == null || expiresAt.isBefore(Instant.now())) {
            return;
        }
        revokedUntil.put(tokenId, expiresAt);
        if (revokedUntil.size() > PRUNE_THRESHOLD) {
            prune();
        }
    }

    public boolean isRevoked(String tokenId) {
        if (tokenId == null) {
            return false;
        }
        Instant expiresAt = revokedUntil.get(tokenId);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt.isBefore(Instant.now())) {
            revokedUntil.remove(tokenId);
            return false;
        }
        return true;
    }

    private void prune() {
        Instant now = Instant.now();
        int before = revokedUntil.size();
        revokedUntil.values().removeIf(expiresAt -> expiresAt.isBefore(now));
        log.debug("Pruned revoked-token store: {} -> {} entries", before, revokedUntil.size());
    }
}
