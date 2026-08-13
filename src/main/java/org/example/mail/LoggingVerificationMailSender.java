package org.example.mail;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(prefix = "app.verification", name = "mail-enabled",
        havingValue = "false", matchIfMissing = true)
@Slf4j
public class LoggingVerificationMailSender implements VerificationMailSender {

    private final Map<String, String> lastCodes = new ConcurrentHashMap<>();

    @PostConstruct
    void warnThisIsNotForProduction() {
        log.warn("app.verification.mail-enabled is off — verification codes will be written to "
                + "this log instead of being emailed. Do not run a real deployment like this.");
    }

    @Override
    public void sendVerificationCode(String email, String code, Duration validFor) {
        lastCodes.put(email, code);
        log.warn("Verification code for {} is {} (valid for {} minutes)",
                email, code, validFor.toMinutes());
    }

    public Optional<String> lastCodeFor(String email) {
        return Optional.ofNullable(lastCodes.get(email));
    }
}
