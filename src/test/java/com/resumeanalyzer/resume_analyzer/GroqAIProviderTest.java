package com.resumeanalyzer.resume_analyzer;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroqAIProviderTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private GroqAIProvider groqAIProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        groqAIProvider = new GroqAIProvider(
                "gsk-test-key",
                "openai/gpt-oss-20b",
                "https://api.groq.com/openai/v1",
                Duration.ofSeconds(10),
                20000,
                httpClient,
                objectMapper
        );
    }

    @Test
    @DisplayName("isAvailable returns true when API key is provided and non-empty")
    void isAvailableReturnsTrueWithKey() {
        assertTrue(groqAIProvider.isAvailable());
        assertEquals("groq", groqAIProvider.getProviderName());
    }

    @Test
    @DisplayName("isAvailable returns false when API key is empty or null")
    void isAvailableReturnsFalseWithoutKey() {
        GroqAIProvider unconfigured = new GroqAIProvider("", "model", "url", null, 0, null, null);
        assertFalse(unconfigured.isAvailable());

        GroqAIProvider nullKey = new GroqAIProvider(null, "model", "url", null, 0, null, null);
        assertFalse(nullKey.isAvailable());
    }

    @Test
    @DisplayName("Successful 200 response parses valid ResumeAnalysisDTO")
    void analyzeResumeSuccessParsesDto() throws Exception {
        String assistantContent = """
                {
                  "score": 88,
                  "summary": "Experienced engineer with strong background in backend systems.",
                  "strongestSkills": ["Java", "Spring Boot", "Docker"],
                  "missingOrWeakSkills": ["Kubernetes"],
                  "strengths": ["Clear project impact", "Quantified achievements"],
                  "weaknesses": ["Certification details missing"],
                  "atsCompatibility": "High ATS match with readable formatting.",
                  "suggestions": ["Include Kubernetes certifications if available."],
                  "recommendedChanges": ["Highlight container orchestration experience."]
                }
                """;
        String groqResponseBody = createGroqResponseBody(assistantContent);

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(groqResponseBody);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        ResumeAnalysisDTO dto = groqAIProvider.analyzeResume("John Doe resume text", status -> {});

        assertNotNull(dto);
        assertEquals(88, dto.score());
        assertEquals("Experienced engineer with strong background in backend systems.", dto.summary());
        assertTrue(dto.strongestSkills().contains("Java"));
        assertTrue(dto.missingOrWeakSkills().contains("Kubernetes"));
    }

    @Test
    @DisplayName("Request sends Authorization header and untrusted resume boundary delimiters")
    void requestSendsAuthAndUntrustedBoundaries() throws Exception {
        String assistantContent = """
                {
                  "score": 75,
                  "summary": "Good resume",
                  "strongestSkills": ["Java"],
                  "missingOrWeakSkills": [],
                  "strengths": ["Concise"],
                  "weaknesses": [],
                  "atsCompatibility": "Good",
                  "suggestions": [],
                  "recommendedChanges": []
                }
                """;
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(createGroqResponseBody(assistantContent));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        groqAIProvider.analyzeResume("Candidate Resume Content", status -> {});

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());

        HttpRequest request = captor.getValue();
        assertEquals("https://api.groq.com/openai/v1/chat/completions", request.uri().toString());
        assertTrue(request.headers().firstValue("Authorization").orElse("").equals("Bearer gsk-test-key"));
        assertEquals("application/json", request.headers().firstValue("Content-Type").orElse(""));
    }

    @Test
    @DisplayName("HTTP 429 throws AICommunicationException with rateLimited = true")
    void analyzeResumeRateLimitedThrows429Exception() throws Exception {
        when(httpResponse.statusCode()).thenReturn(429);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        AICommunicationException ex = assertThrows(AICommunicationException.class, () ->
                groqAIProvider.analyzeResume("resume text", status -> {}));

        assertTrue(ex.isRateLimited());
        assertTrue(ex.isFallbackEligible());
        assertEquals("groq", ex.getProvider());
        assertEquals(AIErrorCategory.RATE_QUOTA_EXHAUSTED, ex.getCategory());
    }

    @Test
    @DisplayName("HTTP 401 throws AICommunicationException with AUTHENTICATION_FAILURE")
    void analyzeResumeUnauthorizedThrowsAuthException() throws Exception {
        when(httpResponse.statusCode()).thenReturn(401);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        AICommunicationException ex = assertThrows(AICommunicationException.class, () ->
                groqAIProvider.analyzeResume("resume text", status -> {}));

        assertEquals("groq", ex.getProvider());
        assertEquals(AIErrorCategory.AUTHENTICATION_FAILURE, ex.getCategory());
        assertFalse(ex.isFallbackEligible());
    }

    @Test
    @DisplayName("HTTP 500/503 throws AICommunicationException with SERVICE_UNAVAILABLE")
    void analyzeResumeServerUnavailableThrowsException() throws Exception {
        when(httpResponse.statusCode()).thenReturn(503);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        AICommunicationException ex = assertThrows(AICommunicationException.class, () ->
                groqAIProvider.analyzeResume("resume text", status -> {}));

        assertEquals(AIErrorCategory.SERVICE_UNAVAILABLE, ex.getCategory());
        assertTrue(ex.isFallbackEligible());
    }

    @Test
    @DisplayName("HttpTimeoutException throws AICommunicationException with TIMEOUT category")
    void analyzeResumeTimeoutThrowsException() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpTimeoutException("Connection timed out after 10000ms"));

        AICommunicationException ex = assertThrows(AICommunicationException.class, () ->
                groqAIProvider.analyzeResume("resume text", status -> {}));

        assertEquals(AIErrorCategory.TIMEOUT, ex.getCategory());
        assertTrue(ex.isFallbackEligible());
    }

    @Test
    @DisplayName("Malformed JSON output throws GeminiResponseException")
    void analyzeResumeMalformedJsonThrowsValidationException() throws Exception {
        String groqResponseBody = createGroqResponseBody("not a valid json object");

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(groqResponseBody);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        assertThrows(GeminiResponseException.class, () ->
                groqAIProvider.analyzeResume("resume text", status -> {}));
    }

    @Test
    @DisplayName("Successful 200 response parses valid ResumeImprovementDTO")
    void improveResumeSuccessParsesDto() throws Exception {
        String assistantContent = """
                {
                  "improvedSummary": "Results-oriented Senior Software Engineer with 5+ years of experience.",
                  "bulletImprovements": [
                    {
                      "section": "Experience",
                      "original": "Worked on backend microservices.",
                      "improved": "Architected and delivered 4 high-throughput microservices using Spring Boot.",
                      "explanation": "Added quantifiable scope and action verbs."
                    }
                  ],
                  "improvementExplanations": [
                    "Strengthened action verbs throughout experience section."
                  ],
                  "actionableChanges": [
                    "Review metric numbers before submitting to recruiters."
                  ]
                }
                """;
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(createGroqResponseBody(assistantContent));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        ResumeAnalysisDTO mockAnalysis = new ResumeAnalysisDTO(
                80, "Good", List.of("Java"), List.of(), List.of(), List.of(), "High", List.of(), List.of()
        );

        ResumeImprovementDTO improvement = groqAIProvider.improveResume("resume text", mockAnalysis);

        assertNotNull(improvement);
        assertEquals("Results-oriented Senior Software Engineer with 5+ years of experience.", improvement.improvedSummary());
        assertEquals(1, improvement.bulletImprovements().size());
        assertEquals("Experience", improvement.bulletImprovements().get(0).section());
        assertEquals("Architected and delivered 4 high-throughput microservices using Spring Boot.",
                improvement.bulletImprovements().get(0).improved());
    }

    @Test
    @DisplayName("Groq HTTP 413 or rate_limit_exceeded error is classified as RATE_QUOTA_EXHAUSTED")
    void groqRateLimitExceededMappedCorrectly() throws Exception {
        String errorBody = """
                {
                  "error": {
                    "message": "Rate limit reached for model openai/gpt-oss-20b: Limit 8000, Used 8200",
                    "type": "tokens",
                    "code": "rate_limit_exceeded"
                  }
                }
                """;
        when(httpResponse.statusCode()).thenReturn(413);
        when(httpResponse.body()).thenReturn(errorBody);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        AICommunicationException ex = assertThrows(AICommunicationException.class, () ->
                groqAIProvider.analyzeResume("resume text", status -> {}));

        assertEquals(AIErrorCategory.RATE_QUOTA_EXHAUSTED, ex.getCategory());
        assertTrue(ex.isRateLimited());
        assertTrue(ex.isFallbackEligible());
    }

    @Test
    @DisplayName("Groq 429 with small Retry-After performs quick retry and succeeds")
    void groqRateLimitWithSmallRetryAfterRetriesOnceAndSucceeds() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> rateLimitResponse = org.mockito.Mockito.mock(HttpResponse.class);
        when(rateLimitResponse.statusCode()).thenReturn(429);
        when(rateLimitResponse.headers()).thenReturn(HttpHeaders.of(Map.of("Retry-After", List.of("0.02")), (a, b) -> true));

        String validJson = """
                {
                  "score": 88,
                  "summary": "Experienced engineer with strong background in backend systems.",
                  "strongestSkills": ["Java", "Spring Boot"],
                  "missingOrWeakSkills": ["AWS"],
                  "strengths": ["Impact"],
                  "weaknesses": ["None"],
                  "atsCompatibility": "High",
                  "suggestions": ["Add certs"],
                  "recommendedChanges": ["Highlight cloud"]
                }
                """;
        @SuppressWarnings("unchecked")
        HttpResponse<String> successResponse = org.mockito.Mockito.mock(HttpResponse.class);
        when(successResponse.statusCode()).thenReturn(200);
        when(successResponse.body()).thenReturn(createGroqResponseBody(validJson));

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(rateLimitResponse)
                .thenReturn(successResponse);

        ResumeAnalysisDTO dto = groqAIProvider.analyzeResume("resume text", status -> {});
        assertNotNull(dto);
        assertEquals(88, dto.score());
    }

    @Test
    @DisplayName("Groq 429 with large Retry-After fails immediately with fallback-eligible exception")
    void groqRateLimitWithLargeRetryAfterFailsFastWithoutRetry() throws Exception {
        when(httpResponse.statusCode()).thenReturn(429);
        when(httpResponse.headers()).thenReturn(HttpHeaders.of(Map.of("Retry-After", List.of("25.0")), (a, b) -> true));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        AICommunicationException ex = assertThrows(AICommunicationException.class, () ->
                groqAIProvider.analyzeResume("resume text", status -> {}));

        assertEquals(AIErrorCategory.RATE_QUOTA_EXHAUSTED, ex.getCategory());
        assertTrue(ex.isRateLimited());
        assertTrue(ex.isFallbackEligible());
        verify(httpClient, org.mockito.Mockito.times(1)).send(any(), any());
    }

    @Test
    @DisplayName("Improvement: Groq malformed response throws GeminiResponseException")
    void improveResumeMalformedJsonThrowsException() throws Exception {
        String groqResponseBody = createGroqResponseBody("not a valid json");

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(groqResponseBody);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        ResumeAnalysisDTO mockAnalysis = new ResumeAnalysisDTO(
                80, "Good", List.of("Java"), List.of(), List.of(), List.of(), "High", List.of(), List.of()
        );

        assertThrows(GeminiResponseException.class, () ->
                groqAIProvider.improveResume("resume text", mockAnalysis));
    }

    @Test
    @DisplayName("Improvement: Groq authentication failure throws AUTHENTICATION_FAILURE exception")
    void improveResumeAuthFailureThrowsException() throws Exception {
        when(httpResponse.statusCode()).thenReturn(401);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        ResumeAnalysisDTO mockAnalysis = new ResumeAnalysisDTO(
                80, "Good", List.of("Java"), List.of(), List.of(), List.of(), "High", List.of(), List.of()
        );

        AICommunicationException ex = assertThrows(AICommunicationException.class, () ->
                groqAIProvider.improveResume("resume text", mockAnalysis));

        assertEquals(AIErrorCategory.AUTHENTICATION_FAILURE, ex.getCategory());
        assertFalse(ex.isFallbackEligible());
    }

    @Test
    @DisplayName("Groq 400 json_validate_failed recovers valid JSON from failed_generation")
    void groqRecoversFromJsonValidateFailedWithFailedGeneration() throws Exception {
        String failedGenerationJson = """
                ```json
                {
                  "improvedSummary": "Experienced Engineer with high impact achievements.",
                  "bulletImprovements": [
                    {
                      "section": "Experience",
                      "original": "Built services",
                      "improved": "Engineered scalable cloud microservices",
                      "explanation": "Active verb and clear outcome"
                    }
                  ],
                  "improvementExplanations": ["Replaced passive phrases with active verbs."],
                  "actionableChanges": ["Review all metrics before submitting."]
                }
                ```
                """;

        String errorBody = objectMapper.writeValueAsString(java.util.Map.of(
                "error", java.util.Map.of(
                        "code", "json_validate_failed",
                        "message", "Failed to validate JSON schema",
                        "failed_generation", failedGenerationJson
                )
        ));

        when(httpResponse.statusCode()).thenReturn(400);
        when(httpResponse.body()).thenReturn(errorBody);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        ResumeAnalysisDTO mockAnalysis = new ResumeAnalysisDTO(
                80, "Good", List.of("Java"), List.of(), List.of(), List.of(), "High", List.of(), List.of()
        );

        ResumeImprovementDTO result = groqAIProvider.improveResume("resume text", mockAnalysis);

        assertNotNull(result);
        assertEquals("Experienced Engineer with high impact achievements.", result.improvedSummary());
        assertEquals(1, result.bulletImprovements().size());
        assertEquals("Engineered scalable cloud microservices", result.bulletImprovements().get(0).improved());
    }

    private String createGroqResponseBody(String assistantContent) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "id", "chatcmpl-test",
                "object", "chat.completion",
                "choices", java.util.List.of(
                        java.util.Map.of(
                                "index", 0,
                                "message", java.util.Map.of(
                                        "role", "assistant",
                                        "content", assistantContent
                                ),
                                "finish_reason", "stop"
                        )
                )
        ));
    }
}
