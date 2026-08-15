package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.dto.MessageResponse;
import org.example.dto.PremiumUserConfirmRequest;
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

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PremiumUserService {

    private final PremiumUserRepository premiumUserRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final CurrentUserService currentUserService;
    private final EmailVerificationService emailVerificationService;
    private final AuditLogService auditLogService;

    @Transactional(noRollbackFor = BadRequestException.class)
    public MessageResponse requestCode(PremiumUserRequest request) {
        Long userId = currentUserService.requireId();
        if (request.getUserId() != null && !request.getUserId().equals(userId)) {
            currentUserService.requireSelf(request.getUserId(), "upgrade your own account");
        }

        String email = normalise(request.getConnectedEmail());
        rejectIfConnectedElsewhere(email, userId);
        premiumUserRepository.findByUserId(userId).ifPresent(existing -> {
            if (existing.getConnectedEmail().equals(email)) {
                throw new BadRequestException("This account is already premium with that address");
            }
        });

        Instant expiresAt = emailVerificationService.issue(userId, email);
        return new MessageResponse("A verification code was sent to " + email
                + ". It stops working at " + expiresAt + ".");
    }

    @Transactional(noRollbackFor = BadRequestException.class)
    public PremiumUserResponse confirm(PremiumUserConfirmRequest request) {
        Long userId = currentUserService.requireId();
        String email = normalise(request.getConnectedEmail());
        rejectIfConnectedElsewhere(email, userId);

        emailVerificationService.consume(userId, email, request.getCode());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> NotFoundException.of("User", userId));
        PremiumUser premiumUser = premiumUserRepository.findByUserId(userId)
                .orElseGet(PremiumUser::new);
        boolean firstTime = premiumUser.getId() == null;

        premiumUser.setUser(user);
        premiumUser.setConnectedEmail(email);
        PremiumUser saved = premiumUserRepository.save(premiumUser);

        grantPremiumRole(user);
        auditLogService.record(userId, null,
                firstTime ? "PREMIUM_ACTIVATED" : "PREMIUM_EMAIL_CHANGED", email, true);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PremiumUserResponse get(Long id) {
        return toResponse(findOwnOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<PremiumUserResponse> list() {
        return premiumUserRepository.findByUserId(currentUserService.requireId())
                .map(PremiumUserService::toResponse)
                .map(List::of)
                .orElseGet(List::of);
    }

    @Transactional
    public void delete(Long id) {
        PremiumUser premiumUser = findOwnOrThrow(id);
        Long userId = premiumUser.getUser().getId();
        String email = premiumUser.getConnectedEmail();
        premiumUserRepository.delete(premiumUser);
        revokePremiumRole(userId);
        auditLogService.record(userId, null, "PREMIUM_DEACTIVATED", email, true);
    }

    private PremiumUser findOwnOrThrow(Long id) {
        PremiumUser premiumUser = premiumUserRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Premium user", id));
        currentUserService.requireSelf(premiumUser.getUser().getId(), "read or change your own premium link");
        return premiumUser;
    }

    private void rejectIfConnectedElsewhere(String email, Long userId) {
        premiumUserRepository.findByConnectedEmail(email).ifPresent(existing -> {
            if (!existing.getUser().getId().equals(userId)) {
                throw new BadRequestException(
                        "Email '" + email + "' is already connected to another account");
            }
        });
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

    private static String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
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
