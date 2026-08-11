package org.example.exception;

/** The request is well-formed but not acceptable, e.g. a username already taken. Rendered as 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
