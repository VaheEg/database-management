package org.example.security;

import org.example.exception.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Answers "who is calling?" for the service layer, so services never have to reach
 * into Spring Security themselves and never have to trust an id sent by the client.
 */
@Service
public class CurrentUserService {

    public Optional<AppUserDetails> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        return authentication.getPrincipal() instanceof AppUserDetails principal
                ? Optional.of(principal)
                : Optional.empty();
    }

    /** The caller, or 403 if the request somehow reached a service unauthenticated. */
    public AppUserDetails require() {
        return currentUser().orElseThrow(
                () -> new AccessDeniedException("No authenticated user is bound to this request"));
    }

    public Long requireId() {
        return require().getId();
    }

    /**
     * Guards operations on somebody else's data.
     *
     * @param ownerUserId the id the row belongs to
     * @param clause      completes the sentence "You may only ..." in the 403 message
     */
    public void requireSelf(Long ownerUserId, String clause) {
        if (ownerUserId == null || !ownerUserId.equals(requireId())) {
            throw new AccessDeniedException("You may only " + clause);
        }
    }
}
