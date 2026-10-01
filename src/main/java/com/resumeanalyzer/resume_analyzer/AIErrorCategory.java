package com.resumeanalyzer.resume_analyzer;

/** Categorizes AI provider communication and execution errors. */
public enum AIErrorCategory {
    RATE_QUOTA_EXHAUSTED,
    AUTHENTICATION_FAILURE,
    SERVICE_UNAVAILABLE,
    TIMEOUT,
    NETWORK_COMMUNICATION,
    INVALID_REQUEST,
    MALFORMED_RESPONSE,
    INTERNAL_VALIDATION
}
