package org.example.service;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.AuthResponse;
import org.example.dto.LoginRequest;
import org.example.dto.RegisterRequest;
import org.example.entity.Role;
import org.example.entity.RoleName;
import org.example.entity.User;
import org.example.entity.UserRole;
import org.example.exception.BadRequestException;
import org.example.repository.RoleRepository;
import org.example.repository.UserRepository;
import org.example.security.AppUserDetails;
import org.example.security.JwtService;
import org.example.security.RevokedTokenStore;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final String ROLE_PREFIX = "ROLE_";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RevokedTokenStore revokedTokenStore;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.getUsername().trim();
        String email = normalizeEmail(request.getEmail());

        if (userRepository.existsByUsername(username)) {
            throw new BadRequestException("Username '" + username + "' is already taken");
        }
        if (email != null && userRepository.existsByEmail(email)) {
            throw new BadRequestException("Email '" + email + "' is already registered");
        }

        Role defaultRole = roleRepository.findByName(RoleName.USER)
                .orElseThrow(() -> new IllegalStateException(
                        "Default role USER is missing — DataSeeder should have created it"));

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEnabled(true);

        UserRole assignment = new UserRole();
        assignment.setUser(user);
        assignment.setRole(defaultRole);
        user.getUserRoles().add(assignment);

        User saved = userRepository.save(user);

        List<String> roles = List.of(defaultRole.getName().name());
        return issueToken(saved.getId(), saved.getUsername(), roles);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        request.getUsername().trim(), request.getPassword()));

        AppUserDetails principal = (AppUserDetails) authentication.getPrincipal();
        assert principal != null;
        List<String> roles = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).filter(Objects::nonNull)
                .map(AuthService::stripRolePrefix)
                .toList();

        return issueToken(principal.getId(), principal.getUsername(), roles);
    }

    public void logout(String authorizationHeader) {
        String token = JwtService.stripBearer(authorizationHeader)
                .orElseThrow(() -> new BadRequestException(
                        "Expected an 'Authorization: Bearer <token>' header to log out"));

        Claims claims = jwtService.parse(token)
                .orElseThrow(() -> new BadRequestException("The presented token is not valid"));

        Instant expiresAt = JwtService.expiresAt(claims).orElse(Instant.now().plus(jwtService.expiration()));
        revokedTokenStore.revoke(claims.getId(), expiresAt);
        log.debug("Token {} for user '{}' revoked", claims.getId(), claims.getSubject());
    }

    private AuthResponse issueToken(Long userId, String username, List<String> roles) {
        JwtService.IssuedToken issued = jwtService.issue(userId, username, roles);
        return AuthResponse.builder()
                .token(issued.token())
                .expiresAt(issued.expiresAt())
                .userId(userId)
                .username(username)
                .roles(roles)
                .build();
    }

    /** The email is optional: a missing or blank one is stored as null, so it never collides. */
    static String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String stripRolePrefix(String authority) {
        return authority.startsWith(ROLE_PREFIX) ? authority.substring(ROLE_PREFIX.length()) : authority;
    }
}
