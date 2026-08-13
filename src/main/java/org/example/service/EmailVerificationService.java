package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.entity.EmailVerification;
import org.example.exception.BadRequestException;
import org.example.mail.VerificationMailSender;
import org.example.mail.VerificationProperties;
import org.example.repository.EmailVerificationRepository;
import org.example.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final int CODE_DIGITS = 6;
    private static final int CODE_BOUND = 1_000_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailVerificationRepository emailVerificationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationMailSender mailSender;
    private final VerificationProperties properties;
    private final AuditLogService auditLogService;

    @Transactional(noRollbackFor = BadRequestException.class)
    public Instant issue(Long userId, String email) {
        emailVerificationRepository.findTopByUserIdOrderByIdDesc(userId).ifPresent(pending -> {
            Instant nextAllowed = pending.getCreatedAt().plus(properties.getResendInterval());
            if (Instant.now().isBefore(nextAllowed)) {
                throw new BadRequestException("A code was sent moments ago — wait "
                        + properties.getResendInterval().toSeconds() + " seconds before asking for another");
            }
        });
        emailVerificationRepository.deleteByUserId(userId);

        String code = generateCode();
        EmailVerification verification = new EmailVerification();
        verification.setUser(userRepository.getReferenceById(userId));
        verification.setEmail(email);
        verification.setCodeHash(passwordEncoder.encode(code));
        verification.setExpiresAt(Instant.now().plus(properties.getCodeTtl()));
        emailVerificationRepository.save(verification);

        mailSender.sendVerificationCode(email, code, properties.getCodeTtl());
        auditLogService.record(userId, null, "EMAIL_VERIFICATION_SENT", "code sent to " + email, true);
        return verification.getExpiresAt();
    }


    @Transactional(noRollbackFor = BadRequestException.class)
    public void consume(Long userId, String email, String code) {
        EmailVerification verification = emailVerificationRepository
                .findTopByUserIdOrderByIdDesc(userId)
                .orElseThrow(() -> new BadRequestException(
                        "No verification code is pending — request one first"));

        if (!verification.getEmail().equals(email)) {
            throw new BadRequestException("The pending code was not requested for that address");
        }
        if (Instant.now().isAfter(verification.getExpiresAt())) {
            discard(verification, userId, "code expired");
            throw new BadRequestException("That code has expired — request a new one");
        }

        if (!passwordEncoder.matches(code, verification.getCodeHash())) {
            verification.setAttempts(verification.getAttempts() + 1);
            int remaining = properties.getMaxAttempts() - verification.getAttempts();
            if (remaining <= 0) {
                discard(verification, userId, "too many incorrect attempts");
                throw new BadRequestException("Too many incorrect attempts — request a new code");
            }
            emailVerificationRepository.save(verification);
            auditLogService.record(userId, null, "EMAIL_VERIFICATION_FAILED",
                    "incorrect code for " + email, false);
            throw new BadRequestException("That code is not correct — " + remaining
                    + " attempt(s) left");
        }

        emailVerificationRepository.delete(verification);
        auditLogService.record(userId, null, "EMAIL_VERIFICATION_CONFIRMED", email, true);
    }

    private void discard(EmailVerification verification, Long userId, String reason) {
        emailVerificationRepository.delete(verification);
        auditLogService.record(userId, null, "EMAIL_VERIFICATION_FAILED", reason, false);
    }

    private static String generateCode() {
        return String.format("%0" + CODE_DIGITS + "d", RANDOM.nextInt(CODE_BOUND));
    }
}
