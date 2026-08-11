package org.example.exception;

/**
 * The caller is authenticated but not allowed to do this — for example acting on a
 * database they neither own nor hold the required permission on. Rendered as 403.
 *
 * <p>Distinct from {@link org.springframework.security.access.AccessDeniedException},
 * which Spring Security raises from the filter chain; this one is thrown by our own
 * services and carries a domain message.
 */
public class AccessDeniedException extends RuntimeException {

    public AccessDeniedException(String message) {
        super(message);
    }
}
