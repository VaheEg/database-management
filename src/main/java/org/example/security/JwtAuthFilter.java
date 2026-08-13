package org.example.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Reads a {@code Bearer} token off the request and, if it verifies and has not been
 * logged out, populates the security context for the rest of the chain.
 *
 * <p>A missing or invalid token is not an error here — the request simply stays
 * anonymous and the authorization rules decide what happens next. That keeps
 * {@code /api/auth/register} and {@code /api/auth/login} usable without a token.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final RevokedTokenStore revokedTokenStore;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<String> token = JwtService.stripBearer(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (token.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        jwtService.parse(token.get())
                // A logged-out token verifies perfectly well; it is the store that rejects it.
                .filter(claims -> !revokedTokenStore.isRevoked(claims.getId()))
                .ifPresent(claims -> authenticate(claims, request));
        filterChain.doFilter(request, response);
    }

    private void authenticate(Claims claims, HttpServletRequest request) {
        String username = claims.getSubject();
        Long userId = userId(claims);
        if (username == null || username.isBlank() || userId == null) {
            return;
        }
        // The principal is an AppUserDetails rebuilt from the token — no password,
        // since nothing downstream re-checks credentials on an already-signed token.
        var principal = new AppUserDetails(userId, username, null, true, authorities(claims));
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private Long userId(Claims claims) {
        Object raw = claims.get(JwtService.CLAIM_USER_ID);
        return raw instanceof Number number ? number.longValue() : null;
    }

    private List<GrantedAuthority> authorities(Claims claims) {
        Object raw = claims.get(JwtService.CLAIM_ROLES);
        if (!(raw instanceof List<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(AppUserDetails.authorityOf(role)))
                .toList();
    }
}
