package org.example.security;

import org.example.exception.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

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

    public AppUserDetails require() {
        return currentUser().orElseThrow(
                () -> new AccessDeniedException("No authenticated user is bound to this request"));
    }

    public Long requireId() {
        return require().getId();
    }

    public void requireSelf(Long ownerUserId, String clause) {
        if (ownerUserId == null || !ownerUserId.equals(requireId())) {
            throw new AccessDeniedException("You may only " + clause);
        }
    }
}
