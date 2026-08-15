package org.example.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.crypto")
@Getter
@Setter
public class CryptoProperties {

    private String secret;
}
