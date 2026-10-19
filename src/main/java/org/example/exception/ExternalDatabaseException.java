package org.example.exception;

/** A registered external database could not be reached or answered with an error outside the statement. */
public class ExternalDatabaseException extends RuntimeException {

    public ExternalDatabaseException(String message) {
        super(message);
    }
}
