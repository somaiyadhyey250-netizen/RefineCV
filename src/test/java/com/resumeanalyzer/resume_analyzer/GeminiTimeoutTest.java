package com.resumeanalyzer.resume_analyzer;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiTimeoutTest {

    @Test
    @DisplayName("GeminiService initializes with default 15s timeout")
    void defaultTimeoutIsFifteenSeconds() {
        GeminiService service = new GeminiService(20000, "fake-api-key");
        assertEquals(Duration.ofSeconds(15), service.getTimeout());
        assertEquals("gemini", service.getProviderName());
        assertTrue(service.isAvailable());
    }

    @Test
    @DisplayName("GeminiService with blank api key is not available")
    void blankApiKeyIsNotAvailable() {
        GeminiService service = new GeminiService(20000, "   ");
        assertFalse(service.isAvailable());
        GeminiCommunicationException ex = assertThrows(GeminiCommunicationException.class,
                () -> service.analyzeResume("some text"));
        assertTrue(ex.isFallbackEligible());
        assertFalse(ex.isRateLimited());
    }

    @Test
    @DisplayName("Custom timeout duration is preserved")
    void customTimeoutDurationPreserved() {
        GeminiService service = new GeminiService(15000, "fake-key", "gemini-3.6-flash", Duration.ofSeconds(10));
        assertEquals(Duration.ofSeconds(10), service.getTimeout());
    }

    @Test
    @DisplayName("GeminiCommunicationException is fallback-eligible for timeouts and rate limits")
    void exceptionPropertiesForFallback() {
        GeminiCommunicationException timeoutEx = new GeminiCommunicationException(
                "Gemini analysis request timed out after 15 seconds.", null, false);
        assertTrue(timeoutEx.isFallbackEligible());
        assertFalse(timeoutEx.isRateLimited());
        assertEquals(AIErrorCategory.NETWORK_COMMUNICATION, timeoutEx.getCategory());

        GeminiCommunicationException rateLimitEx = new GeminiCommunicationException(
                "Gemini quota or rate limit exceeded.", null, true);
        assertTrue(rateLimitEx.isFallbackEligible());
        assertTrue(rateLimitEx.isRateLimited());
        assertEquals(AIErrorCategory.RATE_QUOTA_EXHAUSTED, rateLimitEx.getCategory());
    }
}
