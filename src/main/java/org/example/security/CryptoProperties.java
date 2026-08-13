package org.example.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for {@link CryptoService}, bound from {@code app.crypto.*}.
 */
@ConfigurationProperties(prefix = "app.crypto")
@Getter
@Setter
public class CryptoProperties {

    /**
     * Secret the AES key is derived from. Must be at least 32 characters. Rotating it
     * makes every already-stored external database password undecryptable, so treat it
     * like the database itself and keep it in the environment, not in a config file.
     */
    private String secret;
}
