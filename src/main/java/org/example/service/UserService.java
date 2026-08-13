package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.dto.UserRequest;
import org.example.dto.UserResponse;
import org.example.entity.Role;
import org.example.entity.RoleName;
import org.example.entity.User;
import org.example.entity.UserRole;
import org.example.exception.BadRequestException;
import org.example.exception.NotFoundException;
import org.example.repository.RoleRepository;
import org.example.repository.UserRepository;
import org.example.security.CurrentUserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Accounts. Reads are open to any authenticated caller — usernames and ids have to
 * be discoverable for one user to invite another — but writes are restricted to the
 * caller's own account, since the application has no administrator role.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;

    /**
     * Creates an account. Equivalent to {@code POST /api/auth/register} except that it
     * requires a token and hands back the user rather than a new one.
     */
    @Transactional
    public UserResponse create(UserRequest request) {
        String username = request.getUsername().trim();
        String email = normalizeEmail(request.getEmail());

        if (userRepository.existsByUsername(username)) {
            throw new BadRequestException("Username '" + username + "' is already taken");
        }
        if (userRepository.existsByEmail(email)) {
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

        return toResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return userRepository.findAll().stream().map(UserService::toResponse).toList();
    }

    /** Only your own account, and only the fields you actually sent. */
    @Transactional
    public UserResponse update(Long id, UserRequest request) {
        currentUserService.requireSelf(id, "update your own account");
        User user = findOrThrow(id);

        String username = request.getUsername().trim();
        if (!username.equals(user.getUsername())) {
            if (userRepository.existsByUsername(username)) {
                throw new BadRequestException("Username '" + username + "' is already taken");
            }
            user.setUsername(username);
        }

        String email = normalizeEmail(request.getEmail());
        if (!email.equals(user.getEmail())) {
            if (userRepository.existsByEmail(email)) {
                throw new BadRequestException("Email '" + email + "' is already registered");
            }
            user.setEmail(email);
        }

        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        return toResponse(userRepository.save(user));
    }

    /**
     * Deactivates your own account — a soft delete: {@code enabled} goes to false, so
     * logging in stops working, while the audit trail, the databases you registered and
     * the invitations you sent stay intact and still make sense. Hard-deleting a user an
     * audit log points at would either destroy that history or leave it dangling.
     *
     * <p>One caveat of stateless tokens: a token issued before deactivation keeps working
     * until it expires or its holder calls {@code /api/auth/logout}.
     */
    @Transactional
    public void delete(Long id) {
        currentUserService.requireSelf(id, "deactivate your own account");
        User user = findOrThrow(id);
        user.setEnabled(false);
        userRepository.save(user);
    }

    private User findOrThrow(Long id) {
        return userRepository.findById(id).orElseThrow(() -> NotFoundException.of("User", id));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    static UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .enabled(user.isEnabled())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
