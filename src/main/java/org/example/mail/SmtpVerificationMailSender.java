package org.example.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

        javaMailSender.send(message);
        log.debug("Verification code sent to {}", email);
    }
}
