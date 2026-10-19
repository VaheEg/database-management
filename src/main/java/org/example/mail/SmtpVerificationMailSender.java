package org.example.mail;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.exception.MailDeliveryException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@ConditionalOnProperty(prefix = "app.verification", name = "mail-enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SmtpVerificationMailSender implements VerificationMailSender {

    private final JavaMailSender javaMailSender;
    private final VerificationProperties properties;

    @Value("${spring.mail.host:}")
    private String host;

    @PostConstruct
    void requireAMailServer() {
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("app.verification.mail-enabled is on, but no mail server is set: "
                    + "set spring.mail.host (or SMTP_HOST), plus the port and credentials it needs");
        }
    }

    @Override
    public void sendVerificationCode(String email, String code, Duration validFor) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getMailFrom());
        message.setTo(email);
        message.setSubject("Your Database Manager verification code");
        message.setText("""
                Your verification code is %s

                It is valid for %d minutes and can be used once. If you did not ask to connect
                this address to a Database Manager account, you can ignore this message.
                """.formatted(code, validFor.toMinutes()));

        try {
            javaMailSender.send(message);
        } catch (MailException ex) {
            // The reason (host, credentials, relay refused) is for whoever runs the server, not the caller
            log.error("Could not send the verification code to {}: {}", email, ex.getMessage(), ex);
            throw new MailDeliveryException("The verification email to " + email + " could not be sent — "
                    + "the server's mail settings may be wrong. Try again later.");
        }
        log.debug("Verification code sent to {}", email);
    }
}
