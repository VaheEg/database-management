package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.dto.AccountDeletionRequest;
import org.example.dto.UserRequest;
import org.example.dto.UserResponse;
import org.example.entity.Role;
import org.example.entity.RoleName;
import org.example.entity.User;
import org.example.entity.UserRole;
import org.example.exception.AccessDeniedException;
import org.example.exception.BadRequestException;
import org.example.exception.NotFoundException;
import org.example.repository.EmailVerificationRepository;
import org.example.repository.InvitationRepository;
import org.example.repository.RoleRepository;
import org.example.repository.UserRepository;
import org.example.security.CurrentUserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;
    private final InvitationRepository invitationRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final DbConnectionDetailsService dbConnectionDetailsService;
    private final AuditLogService auditLogService;

    @Transactional
    public UserResponse create(UserRequest request) {
        String username = request.getUsername().trim();
        String email = AuthService.normalizeEmail(request.getEmail());

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

        String email = AuthService.normalizeEmail(request.getEmail());
        if (!Objects.equals(email, user.getEmail())) {
            if (email != null && userRepository.existsByEmail(email)) {
                throw new BadRequestException("Email '" + email + "' is already registered");
            }
            user.setEmail(email);
        }

        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        return toResponse(userRepository.save(user));
    }

    /**
     * Deletes the account for good, after asking for its password again. What exists only through it goes
     * too: its databases (and with them their stored passwords and every share of them), the shares it
     * received, its premium link, profile and pending codes. Audit entries stay, without the account.
     */
    @Transactional
    public void delete(Long id, AccountDeletionRequest request) {
        currentUserService.requireSelf(id, "delete your own account");
        User user = findOrThrow(id);
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AccessDeniedException("That is not the account's password — nothing was deleted");
        }

        int databases = dbConnectionDetailsService.deleteAllOwnedBy(id);
        invitationRepository.deleteAll(invitationRepository.findByToUserId(id));
        invitationRepository.deleteAll(invitationRepository.findByFromUserId(id));
        emailVerificationRepository.deleteByUserId(id);
        auditLogService.detachUser(id);
        userRepository.delete(user);  // the profile, premium link and role assignments cascade
        auditLogService.record(null, null, "ACCOUNT_DELETED",
                "account " + id + " deleted with " + databases + " database(s)", true);
    }

    private User findOrThrow(Long id) {
        return userRepository.findById(id).orElseThrow(() -> NotFoundException.of("User", id));
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
