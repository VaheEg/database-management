package org.example.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class RevokedTokenStore {

    private static final int PRUNE_THRESHOLD = 1_000;

    private final Map<String, Instant> revokedUntil = new ConcurrentHashMap<>();

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
