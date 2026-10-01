package com.resumeanalyzer.resume_analyzer;

/** Base exception for transport, rate-limiting, and communication errors from AI providers. */
public class AICommunicationException extends RuntimeException {

    private final String provider;
    private final AIErrorCategory category;
    private final boolean fallbackEligible;

    public AICommunicationException(String provider, AIErrorCategory category, String message, boolean fallbackEligible, Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.category = category;
        this.fallbackEligible = fallbackEligible;
    }

    public AICommunicationException(String provider, String message, boolean fallbackEligible, Throwable cause) {
        this(provider, AIErrorCategory.NETWORK_COMMUNICATION, message, fallbackEligible, cause);
    }

    public String getProvider() {
        return provider;
    }

    public AIErrorCategory getCategory() {
        return category;
    }

    public boolean isFallbackEligible() {
        return fallbackEligible;
    }

    public boolean isRateLimited() {
        return category == AIErrorCategory.RATE_QUOTA_EXHAUSTED;
    }
}
