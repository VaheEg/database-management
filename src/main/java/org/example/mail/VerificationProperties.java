package org.example.mail;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;


@ConfigurationProperties(prefix = "app.verification")
@Getter
@Setter
public class VerificationProperties {

    private Duration codeTtl = Duration.ofMinutes(10);

    private int maxAttempts = 5;

    private Duration resendInterval = Duration.ofSeconds(60);

    private boolean mailEnabled = false;

    private String mailFrom = "no-reply@db-manager.local";
}
