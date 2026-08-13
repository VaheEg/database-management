package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.dto.PremiumUserRequest;
import org.example.dto.PremiumUserResponse;
import org.example.entity.PremiumUser;
import org.example.entity.Role;
import org.example.entity.RoleName;
import org.example.entity.User;
import org.example.entity.UserRole;
import org.example.exception.BadRequestException;
import org.example.exception.NotFoundException;
import org.example.repository.PremiumUserRepository;
import org.example.repository.RoleRepository;
import org.example.repository.UserRepository;
import org.example.repository.UserRoleRepository;
import org.example.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Premium status, which a user gains by connecting an email address.
 *
 * <p>Connecting the email does two things: it records the link here, and it adds the
 * application-level {@link RoleName#PREMIUM} role to the account. Roles are baked into
 * a token when it is issued, so the change shows up in {@code /api/auth/me} only after
 * the user logs in again.
 */
@Service
@RequiredArgsConstructor
public class PremiumUserService {

    private final PremiumUserRepository premiumUserRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final CurrentUserService currentUserService;

    @Transactional
    public PremiumUserResponse create(PremiumUserRequest request) {
        Long userId = currentUserService.requireId();
        if (request.getUserId() != null && !request.getUserId().equals(userId)) {
            currentUserService.requireSelf(request.getUserId(), "upgrade your own account");
        }
        if (premiumUserRepository.existsByUserId(userId)) {
            throw new BadRequestException("This account is already premium");
        }

        String email = request.getConnectedEmail().trim().toLowerCase(Locale.ROOT);
        premiumUserRepository.findByConnectedEmail(email).ifPresent(existing -> {
            throw new BadRequestException("Email '" + email + "' is already connected to another account");
        });

        User user = userRepository.findById(userId)
                .orElseThrow(() -> NotFoundException.of("User", userId));

        PremiumUser premiumUser = new PremiumUser();
        premiumUser.setUser(user);
        premiumUser.setConnectedEmail(email);
        PremiumUser saved = premiumUserRepository.save(premiumUser);

        grantPremiumRole(user);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PremiumUserResponse get(Long id) {
        return toResponse(findOwnOrThrow(id));
    }

    /** Your own premium link, as a list of zero or one. */
    @Transactional(readOnly = true)
    public List<PremiumUserResponse> list() {
        return premiumUserRepository.findByUserId(currentUserService.requireId())
                .map(PremiumUserService::toResponse)
                .map(List::of)
                .orElseGet(List::of);
    }

    /** Changes the connected email; premium status itself is unaffected. */
    @Transactional
    public PremiumUserResponse update(Long id, PremiumUserRequest request) {
        PremiumUser premiumUser = findOwnOrThrow(id);
        String email = request.getConnectedEmail().trim().toLowerCase(Locale.ROOT);

        premiumUserRepository.findByConnectedEmail(email).ifPresent(existing -> {
            if (!existing.getId().equals(premiumUser.getId())) {
                throw new BadRequestException("Email '" + email + "' is already connected to another account");
            }
        });

        premiumUser.setConnectedEmail(email);
        return toResponse(premiumUserRepository.save(premiumUser));
    }

    /** Disconnects the email, which drops the account back to a normal user. */
    @Transactional
    public void delete(Long id) {
        PremiumUser premiumUser = findOwnOrThrow(id);
        Long userId = premiumUser.getUser().getId();
        premiumUserRepository.delete(premiumUser);
        revokePremiumRole(userId);
    }

    private PremiumUser findOwnOrThrow(Long id) {
        PremiumUser premiumUser = premiumUserRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Premium user", id));
        currentUserService.requireSelf(premiumUser.getUser().getId(), "read or change your own premium link");
        return premiumUser;
    }

    private void grantPremiumRole(User user) {
        Role premium = roleRepository.findByName(RoleName.PREMIUM)
                .orElseThrow(() -> new IllegalStateException(
                        "Role PREMIUM is missing — DataSeeder should have created it"));
        if (userRoleRepository.existsByUserIdAndRoleId(user.getId(), premium.getId())) {
            return;
        }
        UserRole assignment = new UserRole();
        assignment.setUser(user);
        assignment.setRole(premium);
        userRoleRepository.save(assignment);
    }

    private void revokePremiumRole(Long userId) {
        roleRepository.findByName(RoleName.PREMIUM)
                .flatMap(premium -> userRoleRepository.findByUserIdAndRoleId(userId, premium.getId()))
                .ifPresent(userRoleRepository::delete);
    }

    private static PremiumUserResponse toResponse(PremiumUser premiumUser) {
        return PremiumUserResponse.builder()
                .id(premiumUser.getId())
                .userId(premiumUser.getUser().getId())
                .connectedEmail(premiumUser.getConnectedEmail())
                .activatedAt(premiumUser.getActivatedAt())
                .build();
    }
}
