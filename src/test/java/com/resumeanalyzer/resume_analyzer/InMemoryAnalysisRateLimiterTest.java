package com.resumeanalyzer.resume_analyzer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class InMemoryAnalysisRateLimiterTest {

    @Test
    void allowsRequestsUpToConfiguredLimit() {
        InMemoryAnalysisRateLimiter limiter =
                new InMemoryAnalysisRateLimiter(2, Duration.ofMinutes(1), 10);

        assertTrue(limiter.tryAcquire("client-a"));
        assertTrue(limiter.tryAcquire("client-a"));
    }

    @Test
    void rejectsRequestsBeyondConfiguredLimit() {
        InMemoryAnalysisRateLimiter limiter =
                new InMemoryAnalysisRateLimiter(2, Duration.ofMinutes(1), 10);

        assertTrue(limiter.tryAcquire("client-a"));
        assertTrue(limiter.tryAcquire("client-a"));
        assertFalse(limiter.tryAcquire("client-a"));
    }

    @Test
    void respectsConfiguredClientTrackingCapacityAndRejectsMissingKeys() {
        InMemoryAnalysisRateLimiter limiter =
                new InMemoryAnalysisRateLimiter(5, Duration.ofMinutes(1), 1);

        assertTrue(limiter.tryAcquire("client-a"));
        assertFalse(limiter.tryAcquire("client-b"));
        assertFalse(limiter.tryAcquire(" "));
    }
}
