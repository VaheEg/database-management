package org.example.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * The single error shape every failed request returns.
 *
 * @param timestamp when the failure was rendered
 * @param status    HTTP status code
 * @param error     HTTP reason phrase
 * @param message   human-readable explanation
 * @param path      the request URI that failed
 * @param fieldErrors per-field validation messages, present only for 400s from {@code @Valid}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, null);
    }
}
