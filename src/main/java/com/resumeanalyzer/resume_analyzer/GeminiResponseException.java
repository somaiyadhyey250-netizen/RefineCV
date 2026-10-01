package com.resumeanalyzer.resume_analyzer;

/** Indicates that a provider response could not satisfy RefineCV's analysis contract. */
public class GeminiResponseException extends RuntimeException {

    public enum Reason {
        MALFORMED_JSON,
        INVALID_STRUCTURE,
        RESPONSE_TOO_LARGE
    }

    private final Reason reason;

    public GeminiResponseException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public GeminiResponseException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
