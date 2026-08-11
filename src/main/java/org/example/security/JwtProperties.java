package org.example.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT settings, bound from {@code app.jwt.*}.
 *
 * <p>The secret should come from the environment in any real deployment; the
 * default in {@code application.properties} exists so the app boots for local work.
 */
@ConfigurationProperties(prefix = "app.jwt")
@Getter
@Setter
public class JwtProperties {

    /** HMAC-SHA signing secret. Must be at least 32 characters (256 bits) for HS256. */
    private String secret;

    /** How long an issued access token stays valid. */
    private Duration expiration = Duration.ofHours(12);

    /** Value placed in the {@code iss} claim. */
    private String issuer = "db-manager";
}
