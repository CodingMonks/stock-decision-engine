package com.saurabh.stockdecision.model;

/**
 * Why Jev could not evaluate a decision. The decision itself is still returned,
 * computed by the basic rules.
 *
 * @param code        error category
 * @param message     human-readable cause
 * @param httpStatus  HTTP status returned by Jev, or null when no response was received
 * @param requestId   Jev request id for support, or null when unavailable
 */
public record JevError(Code code, String message, Integer httpStatus, String requestId) {

    public enum Code {
        AUTHENTICATION,
        PERMISSION_DENIED,
        BAD_REQUEST,
        NOT_FOUND,
        RATE_LIMITED,
        SERVER_ERROR,
        TIMEOUT,
        CONNECTION,
        INVALID_RESPONSE,
        UNKNOWN;

        /** Errors caused by this service's configuration rather than a transient Jev problem. */
        public boolean isConfigurationProblem() {
            return this == AUTHENTICATION || this == PERMISSION_DENIED || this == BAD_REQUEST || this == NOT_FOUND;
        }
    }
}
