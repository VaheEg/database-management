package org.example.mail;

import java.time.Duration;

public interface VerificationMailSender {

    void sendVerificationCode(String email, String code, Duration validFor);
}
