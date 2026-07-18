package dev.ayo.devinbridge.devin;

/**
 * Wraps any failure talking to the Devin API (non-2xx response, network error).
 */
public final class DevinApiException extends RuntimeException {

    public DevinApiException(String message) {
        super(message);
    }

    public DevinApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
