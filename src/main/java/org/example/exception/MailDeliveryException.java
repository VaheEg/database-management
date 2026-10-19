package org.example.exception;

/** The mail server would not take a verification email. The details are in the server log, not the reply. */
public class MailDeliveryException extends RuntimeException {

    public MailDeliveryException(String message) {
        super(message);
    }
}
