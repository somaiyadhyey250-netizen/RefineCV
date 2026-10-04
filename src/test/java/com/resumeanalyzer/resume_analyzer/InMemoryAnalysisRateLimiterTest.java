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

    @Test
    void allowsTenRequestsInNewBaselineWindowAndRejectsEleventh() {
        InMemoryAnalysisRateLimiter limiter =
                new InMemoryAnalysisRateLimiter(10, Duration.ofMinutes(15), 10000);

        for (int i = 1; i <= 10; i++) {
            assertTrue(limiter.tryAcquire("127.0.0.1"), "Request " + i + " should be allowed");
        }
        assertFalse(limiter.tryAcquire("127.0.0.1"), "11th request should be rejected by 10/15m rate limit");
    }
}
