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
