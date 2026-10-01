package com.resumeanalyzer.resume_analyzer;

/** Provider configuration or transport failure, kept distinct from invalid model output. */
public class GeminiCommunicationException extends AICommunicationException {

    public GeminiCommunicationException(String message, Throwable cause) {
        this(message, cause, false);
    }

    public GeminiCommunicationException(String message, Throwable cause, boolean rateLimited) {
        super("gemini", rateLimited ? AIErrorCategory.RATE_QUOTA_EXHAUSTED : AIErrorCategory.NETWORK_COMMUNICATION,
                message, true, cause);
    }
}
