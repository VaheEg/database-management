package org.example.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.query")
@Getter
@Setter
public class QueryProperties {

    /** How long to wait for a registered database to accept a connection. */
    private Duration connectTimeout = Duration.ofSeconds(10);

    /** Statement timeout for every query run against a registered database. */
    private Duration statementTimeout = Duration.ofSeconds(30);

    /** The most rows one query returns; the rest is cut off and the response says so. */
    private int maxRows = 500;
}
