package com.resumeanalyzer.resume_analyzer;

import java.util.List;
import java.util.concurrent.CancellationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FallbackAIServiceTest {

    @Mock
    private AIProvider primaryProvider;

    @Mock
    private AIProvider fallbackProvider;

    private FallbackAIService fallbackAIService;

    private final ResumeAnalysisDTO sampleAnalysis = new ResumeAnalysisDTO(
            85, "Solid background", List.of("Java"), List.of(), List.of("Leadership"),
            List.of(), "High", List.of("Add metrics"), List.of()
    );

    private final ResumeImprovementDTO sampleImprovement = new ResumeImprovementDTO(
            "Improved professional summary",
            List.of(new BulletImprovementDTO("Experience", "old", "new", "clarity")),
            List.of("Explanation"),
            List.of("Action")
    );

    @BeforeEach
    void setUp() {
        fallbackAIService = new FallbackAIService(primaryProvider, fallbackProvider);
    }

    @Test
    @DisplayName("When primary (Gemini) succeeds, fallback provider (Groq) is never invoked")
    void primarySuccessDoesNotInvokeFallback() {
        when(primaryProvider.analyzeResume(eq("resume text"), any())).thenReturn(sampleAnalysis);
        when(primaryProvider.getProviderName()).thenReturn("gemini");

        AIAnalysisResult result = fallbackAIService.analyzeResumeWithProvider("resume text", status -> {});

        assertNotNull(result);
        assertEquals("gemini", result.providerName());
        assertEquals(85, result.analysis().score());
        verify(fallbackProvider, never()).analyzeResume(any(), any());
    }

    @Test
    @DisplayName("When primary fails with 429 quota exhaustion, gracefully falls back to Groq")
    void primaryRateLimitFallsBackToGroq() {
        when(primaryProvider.analyzeResume(eq("resume text"), any()))
                .thenThrow(new GeminiCommunicationException("Resource exhausted: 429", null, true));
        when(primaryProvider.getProviderName()).thenReturn("gemini");
        when(fallbackProvider.isAvailable()).thenReturn(true);
        when(fallbackProvider.getProviderName()).thenReturn("groq");
        when(fallbackProvider.analyzeResume(eq("resume text"), any())).thenReturn(sampleAnalysis);

        AIAnalysisResult result = fallbackAIService.analyzeResumeWithProvider("resume text", status -> {});

        assertNotNull(result);
        assertEquals("groq", result.providerName());
        assertEquals(85, result.analysis().score());
        verify(fallbackProvider).analyzeResume(eq("resume text"), any());
    }

    @Test
    @DisplayName("When primary fails with communication error / 5xx, falls back to Groq")
    void primaryCommunicationFailureFallsBackToGroq() {
        when(primaryProvider.analyzeResume(eq("resume text"), any()))
                .thenThrow(new GeminiCommunicationException("Connection reset", null, false));
        when(primaryProvider.getProviderName()).thenReturn("gemini");
        when(fallbackProvider.isAvailable()).thenReturn(true);
        when(fallbackProvider.getProviderName()).thenReturn("groq");
        when(fallbackProvider.analyzeResume(eq("resume text"), any())).thenReturn(sampleAnalysis);

        AIAnalysisResult result = fallbackAIService.analyzeResumeWithProvider("resume text", status -> {});

        assertEquals("groq", result.providerName());
        verify(fallbackProvider).analyzeResume(eq("resume text"), any());
    }

    @Test
    @DisplayName("When primary fails with contract validation error, DO NOT fallback (bug in provider response)")
    void primaryContractValidationErrorDoesNotFallback() {
        when(primaryProvider.analyzeResume(eq("resume text"), any()))
                .thenThrow(new AnalysisContractValidationException(
                        AnalysisContractValidationException.Reason.SCORE_OUT_OF_RANGE));

        assertThrows(AnalysisContractValidationException.class, () ->
                fallbackAIService.analyzeResumeWithProvider("resume text", status -> {}));

        verify(fallbackProvider, never()).analyzeResume(any(), any());
    }

    @Test
    @DisplayName("When primary fails with GeminiResponseException, DO NOT fallback")
    void primaryResponseExceptionDoesNotFallback() {
        when(primaryProvider.analyzeResume(eq("resume text"), any()))
                .thenThrow(new GeminiResponseException(
                        GeminiResponseException.Reason.MALFORMED_JSON, "Invalid JSON"));

        assertThrows(GeminiResponseException.class, () ->
                fallbackAIService.analyzeResumeWithProvider("resume text", status -> {}));

        verify(fallbackProvider, never()).analyzeResume(any(), any());
    }

    @Test
    @DisplayName("When request is cancelled, DO NOT fallback")
    void primaryCancellationDoesNotFallback() {
        when(primaryProvider.analyzeResume(eq("resume text"), any()))
                .thenThrow(new CancellationException("Client disconnected"));

        assertThrows(CancellationException.class, () ->
                fallbackAIService.analyzeResumeWithProvider("resume text", status -> {}));

        verify(fallbackProvider, never()).analyzeResume(any(), any());
    }

    @Test
    @DisplayName("When primary fails but Groq is unavailable, rethrows primary exception")
    void primaryFailsGroqUnavailableRethrows() {
        when(primaryProvider.analyzeResume(eq("resume text"), any()))
                .thenThrow(new GeminiCommunicationException("Rate limit", null, true));
        when(fallbackProvider.isAvailable()).thenReturn(false);

        assertThrows(GeminiCommunicationException.class, () ->
                fallbackAIService.analyzeResumeWithProvider("resume text", status -> {}));

        verify(fallbackProvider, never()).analyzeResume(any(), any());
    }

    @Test
    @DisplayName("When primary fails and Groq also fails, throws Groq's error")
    void primaryFailsGroqAlsoFailsRethrows() {
        when(primaryProvider.analyzeResume(eq("resume text"), any()))
                .thenThrow(new GeminiCommunicationException("Gemini down", null, false));
        when(primaryProvider.getProviderName()).thenReturn("gemini");
        when(fallbackProvider.isAvailable()).thenReturn(true);
        when(fallbackProvider.getProviderName()).thenReturn("groq");
        when(fallbackProvider.analyzeResume(eq("resume text"), any()))
                .thenThrow(new AICommunicationException("groq", AIErrorCategory.SERVICE_UNAVAILABLE, "Groq down", true, null));

        assertThrows(AICommunicationException.class, () ->
                fallbackAIService.analyzeResumeWithProvider("resume text", status -> {}));
    }

    /* =========================================================
       RESUME IMPROVEMENT TESTS
       ========================================================= */

    @Test
    @DisplayName("Improvement: When primary succeeds, Groq is not invoked")
    void improvementPrimarySuccessDoesNotInvokeFallback() {
        when(primaryProvider.improveResume(eq("resume text"), eq(sampleAnalysis)))
                .thenReturn(sampleImprovement);
        when(primaryProvider.getProviderName()).thenReturn("gemini");

        AIImprovementResult result = fallbackAIService.improveResumeWithProvider("resume text", sampleAnalysis);

        assertNotNull(result);
        assertEquals("gemini", result.providerName());
        assertEquals("Improved professional summary", result.improvement().improvedSummary());
        verify(fallbackProvider, never()).improveResume(any(), any());
    }

    @Test
    @DisplayName("Improvement: When primary is rate limited, falls back to Groq")
    void improvementPrimaryRateLimitFallsBackToGroq() {
        when(primaryProvider.improveResume(eq("resume text"), eq(sampleAnalysis)))
                .thenThrow(new GeminiCommunicationException("429 Quota Exceeded", null, true));
        when(primaryProvider.getProviderName()).thenReturn("gemini");
        when(fallbackProvider.isAvailable()).thenReturn(true);
        when(fallbackProvider.getProviderName()).thenReturn("groq");
        when(fallbackProvider.improveResume(eq("resume text"), eq(sampleAnalysis)))
                .thenReturn(sampleImprovement);

        AIImprovementResult result = fallbackAIService.improveResumeWithProvider("resume text", sampleAnalysis);

        assertNotNull(result);
        assertEquals("groq", result.providerName());
        assertEquals("Improved professional summary", result.improvement().improvedSummary());
        verify(fallbackProvider).improveResume(eq("resume text"), eq(sampleAnalysis));
    }

    @Test
    @DisplayName("Improvement: When contract validation fails, DO NOT fallback")
    void improvementContractValidationDoesNotFallback() {
        when(primaryProvider.improveResume(eq("resume text"), eq(sampleAnalysis)))
                .thenThrow(new AnalysisContractValidationException(
                        AnalysisContractValidationException.Reason.BLANK_REQUIRED_TEXT));

        assertThrows(AnalysisContractValidationException.class, () ->
                fallbackAIService.improveResumeWithProvider("resume text", sampleAnalysis));

        verify(fallbackProvider, never()).improveResume(any(), any());
    }

    @Test
    @DisplayName("Improvement: When both primary and fallback fail, throws fallback exception")
    void improvementBothProvidersFailThrowsException() {
        when(primaryProvider.improveResume(eq("resume text"), eq(sampleAnalysis)))
                .thenThrow(new GeminiCommunicationException("Gemini 429", null, true));
        when(primaryProvider.getProviderName()).thenReturn("gemini");
        when(fallbackProvider.isAvailable()).thenReturn(true);
        when(fallbackProvider.getProviderName()).thenReturn("groq");
        when(fallbackProvider.improveResume(eq("resume text"), eq(sampleAnalysis)))
                .thenThrow(new AICommunicationException("groq", AIErrorCategory.SERVICE_UNAVAILABLE, "Groq 503", true, null));

        assertThrows(AICommunicationException.class, () ->
                fallbackAIService.improveResumeWithProvider("resume text", sampleAnalysis));
    }

    @Test
    @DisplayName("When primary times out with GeminiCommunicationException, immediately triggers Groq and emits ai-fallback")
    void primaryTimeoutTriggersGroqFallbackAndEmitsProgress() {
        java.util.List<String> emittedEvents = new java.util.ArrayList<>();
        when(primaryProvider.analyzeResume(eq("resume text"), any()))
                .thenThrow(new GeminiCommunicationException("Gemini analysis request timed out after 15 seconds.", null, false));
        when(primaryProvider.getProviderName()).thenReturn("gemini");
        when(fallbackProvider.isAvailable()).thenReturn(true);
        when(fallbackProvider.getProviderName()).thenReturn("groq");
        when(fallbackProvider.analyzeResume(eq("resume text"), any())).thenReturn(sampleAnalysis);

        AIAnalysisResult result = fallbackAIService.analyzeResumeWithProvider("resume text", emittedEvents::add);

        assertNotNull(result);
        assertEquals("groq", result.providerName());
        assertEquals(85, result.analysis().score());
        org.junit.jupiter.api.Assertions.assertTrue(emittedEvents.contains("ai-fallback"));
        verify(fallbackProvider).analyzeResume(eq("resume text"), any());
    }

    @Test
    @DisplayName("FallbackAIService initializes with groq primary when preferredPrimary is groq")
    void groqPrimaryInitialization() {
        GeminiService geminiMock = org.mockito.Mockito.mock(GeminiService.class);
        GroqAIProvider groqMock = org.mockito.Mockito.mock(GroqAIProvider.class);
        when(groqMock.getProviderName()).thenReturn("groq");

        FallbackAIService service = new FallbackAIService(geminiMock, groqMock, "groq");

        assertEquals("groq", service.getProviderName());
        assertEquals(groqMock, service.getPrimaryProvider());
        assertEquals(geminiMock, service.getFallbackProvider());
    }
}
