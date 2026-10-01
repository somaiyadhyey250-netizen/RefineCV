package com.resumeanalyzer.resume_analyzer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Groq OpenAI-compatible AI provider using structured JSON outputs. */
@Component("groqAIProvider")
public class GroqAIProvider implements AIProvider {

    private static final Logger logger = LoggerFactory.getLogger(GroqAIProvider.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final Duration timeout;
    private final int maxResponseCharacters;

    @Autowired
    public GroqAIProvider(
            @Value("${refinecv.groq.api-key:}") String apiKey,
            @Value("${refinecv.groq.model:openai/gpt-oss-20b}") String model,
            @Value("${refinecv.groq.base-url:https://api.groq.com/openai/v1}") String baseUrl,
            @Value("${refinecv.groq.timeout:60s}") Duration timeout,
            @Value("${refinecv.groq.max-response-characters:20000}") int maxResponseCharacters
    ) {
        this(apiKey, model, baseUrl, timeout, maxResponseCharacters,
                HttpClient.newBuilder().connectTimeout(timeout).build(),
                new ObjectMapper());
    }

    public GroqAIProvider(
            String apiKey,
            String model,
            String baseUrl,
            Duration timeout,
            int maxResponseCharacters,
            HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = (model == null || model.isBlank()) ? "openai/gpt-oss-20b" : model.trim();
        this.baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "https://api.groq.com/openai/v1" : baseUrl.trim();
        this.timeout = timeout != null ? timeout : Duration.ofSeconds(60);
        this.maxResponseCharacters = maxResponseCharacters > 0 ? maxResponseCharacters : 20000;
        this.httpClient = httpClient != null ? httpClient : HttpClient.newBuilder().connectTimeout(this.timeout).build();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public String getProviderName() {
        return "groq";
    }

    @Override
    public boolean isAvailable() {
        return !apiKey.isEmpty();
    }

    @Override
    public ResumeAnalysisDTO analyzeResume(String resumeText, Consumer<String> progress) {
        if (!isAvailable()) {
            throw new AICommunicationException(getProviderName(), AIErrorCategory.AUTHENTICATION_FAILURE,
                    "Groq is not configured with an API key.", false, null);
        }

        String prompt = AIPromptBuilder.buildAnalysisPrompt(resumeText);
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", "You are an expert AI Resume Analyzer. Output strictly valid JSON matching the schema. Do not output markdown code fences or conversational text."),
                        Map.of("role", "user", "content", prompt)
                ),
                "response_format", Map.of(
                        "type", "json_schema",
                        "json_schema", Map.of(
                                "name", "resume_analysis",
                                "strict", true,
                                "schema", buildAnalysisJsonSchema()
                        )
                ),
                "temperature", 0.2,
                "max_completion_tokens", 8192,
                "reasoning_format", "parsed"
        );

        if (progress != null) {
            progress.accept("ai-analysis");
        }

        String responseContent = executeChatCompletion(requestBody, "analysis");

        if (progress != null) {
            progress.accept("recommendations");
        }

        return AIResponseParser.parseAndValidateAnalysis(responseContent, maxResponseCharacters, objectMapper, getProviderName());
    }

    @Override
    public ResumeImprovementDTO improveResume(String resumeText, ResumeAnalysisDTO analysis) {
        if (!isAvailable()) {
            throw new AICommunicationException(getProviderName(), AIErrorCategory.AUTHENTICATION_FAILURE,
                    "Groq is not configured with an API key.", false, null);
        }

        String prompt = AIPromptBuilder.buildImprovementPrompt(resumeText, analysis);
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", "You are an expert AI Resume Coach. Output strictly valid JSON matching the schema. Do not output markdown code fences or conversational text."),
                        Map.of("role", "user", "content", prompt)
                ),
                "response_format", Map.of(
                        "type", "json_schema",
                        "json_schema", Map.of(
                                "name", "resume_improvement",
                                "strict", true,
                                "schema", buildImprovementJsonSchema()
                        )
                ),
                "temperature", 0.2,
                "max_completion_tokens", 8192,
                "reasoning_format", "parsed"
        );

        String responseContent = executeChatCompletion(requestBody, "improvement");
        return AIResponseParser.parseAndValidateImprovement(responseContent, maxResponseCharacters, objectMapper, getProviderName());
    }

    private String executeChatCompletion(Map<String, Object> requestBody, String operation) {
        return executeChatCompletionWithRetry(requestBody, operation, true);
    }

    private String executeChatCompletionWithRetry(Map<String, Object> requestBody, String operation, boolean allowRetry) {
        final String requestPayload;
        try {
            requestPayload = objectMapper.writeValueAsString(requestBody);
        } catch (Exception e) {
            throw new AICommunicationException(getProviderName(), AIErrorCategory.INVALID_REQUEST,
                    "Failed to serialize Groq request.", false, e);
        }

        String endpointUrl = (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl) + "/chat/completions";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpointUrl))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(requestPayload))
                .build();

        long startTime = System.currentTimeMillis();
        final HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.warn("provider=groq operation={} status=timeout durationMs={}", operation, duration);
            throw new AICommunicationException(getProviderName(), AIErrorCategory.TIMEOUT,
                    "Groq request timed out.", true, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Groq request was interrupted.");
        } catch (IOException e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("provider=groq operation={} status=io_error durationMs={}", operation, duration);
            throw new AICommunicationException(getProviderName(), AIErrorCategory.NETWORK_COMMUNICATION,
                    "Groq network communication failed.", true, e);
        }

        long duration = System.currentTimeMillis() - startTime;
        int status = response.statusCode();

        if (status == 200) {
            logger.info("provider=groq operation={} status=200 durationMs={}", operation, duration);
            return extractMessageContent(response.body(), operation);
        }

        String safeErrorMsg = null;
        String errorCode = null;
        String failedGeneration = null;
        try {
            if (response.body() != null && !response.body().isBlank()) {
                JsonNode errRoot = objectMapper.readTree(response.body());
                if (errRoot.has("error")) {
                    JsonNode errObj = errRoot.get("error");
                    if (errObj.has("code") && !errObj.get("code").isNull()) {
                        errorCode = errObj.get("code").asText();
                    }
                    if (errObj.has("message") && !errObj.get("message").isNull()) {
                        safeErrorMsg = errObj.get("message").asText();
                    }
                    if (errObj.has("failed_generation") && !errObj.get("failed_generation").isNull()) {
                        failedGeneration = errObj.get("failed_generation").asText();
                    }
                }
            }
        } catch (Exception ignored) {}

        if ("json_validate_failed".equalsIgnoreCase(errorCode)) {
            if (failedGeneration != null && !failedGeneration.isBlank()) {
                String clean = AIResponseParser.cleanJson(failedGeneration);
                if (clean.startsWith("{") && clean.endsWith("}")) {
                    try {
                        objectMapper.readTree(clean);
                        logger.info("provider=groq operation={} recovered_from=failed_generation durationMs={}", operation, duration);
                        return clean;
                    } catch (Exception ignored) {}
                }
            }
            if (allowRetry) {
                logger.warn("provider=groq operation={} status=400 code=json_validate_failed retrying=true durationMs={}", operation, duration);
                java.util.Map<String, Object> retryBody = new java.util.HashMap<>(requestBody);
                retryBody.put("temperature", 0.1);
                return executeChatCompletionWithRetry(retryBody, operation, false);
            }
        }

        if (status == 429 || status == 413 || "rate_limit_exceeded".equalsIgnoreCase(errorCode)) {
            logger.warn("provider=groq operation={} status={} code={} category=RATE_QUOTA_EXHAUSTED durationMs={}",
                    operation, status, errorCode != null ? errorCode : "none", duration);
            throw new AICommunicationException(getProviderName(), AIErrorCategory.RATE_QUOTA_EXHAUSTED,
                    "Groq rate limit or quota exceeded (HTTP " + status + ").", true, null);
        }

        if (status == 401 || status == 403) {
            logger.error("provider=groq operation={} status={} category=AUTHENTICATION_FAILURE durationMs={}", operation, status, duration);
            throw new AICommunicationException(getProviderName(), AIErrorCategory.AUTHENTICATION_FAILURE,
                    "Groq authentication failed (HTTP " + status + ").", false, null);
        }

        if (status >= 500 && status < 600) {
            logger.error("provider=groq operation={} status={} category=SERVICE_UNAVAILABLE durationMs={}", operation, status, duration);
            throw new AICommunicationException(getProviderName(), AIErrorCategory.SERVICE_UNAVAILABLE,
                    "Groq server error (HTTP " + status + ").", true, null);
        }

        logger.error("provider=groq operation={} status={} code={} category=UNEXPECTED errorMsg={} durationMs={}",
                operation, status, errorCode != null ? errorCode : "none", safeErrorMsg != null ? safeErrorMsg : "none", duration);
        throw new AICommunicationException(getProviderName(), AIErrorCategory.NETWORK_COMMUNICATION,
                "Groq returned unexpected HTTP status: " + status + (safeErrorMsg != null ? ": " + safeErrorMsg : ""), false, null);
    }

    private String extractMessageContent(String responseJson, String operation) {
        if (responseJson == null || responseJson.isBlank()) {
            throw new GeminiResponseException(GeminiResponseException.Reason.MALFORMED_JSON,
                    "Groq returned an empty response body.");
        }

        final JsonNode root;
        try {
            root = objectMapper.readTree(responseJson);
        } catch (Exception e) {
            throw new GeminiResponseException(GeminiResponseException.Reason.MALFORMED_JSON,
                    "Groq returned unparseable JSON response.", e);
        }

        JsonNode choices = root.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            throw new GeminiResponseException(GeminiResponseException.Reason.INVALID_STRUCTURE,
                    "Groq response missing 'choices' array.");
        }

        JsonNode firstChoice = choices.get(0);
        JsonNode message = firstChoice.get("message");
        if (message == null || !message.isObject()) {
            throw new GeminiResponseException(GeminiResponseException.Reason.INVALID_STRUCTURE,
                    "Groq choice missing 'message' object.");
        }

        JsonNode content = message.get("content");
        if (content == null || !content.isTextual() || content.asText().isBlank()) {
            throw new GeminiResponseException(GeminiResponseException.Reason.MALFORMED_JSON,
                    "Groq returned empty text content in completion message.");
        }

        return content.asText();
    }

    private Map<String, Object> buildAnalysisJsonSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "score", Map.of("type", "integer"),
                        "summary", Map.of("type", "string"),
                        "strongestSkills", Map.of("type", "array", "items", Map.of("type", "string")),
                        "missingOrWeakSkills", Map.of("type", "array", "items", Map.of("type", "string")),
                        "strengths", Map.of("type", "array", "items", Map.of("type", "string")),
                        "weaknesses", Map.of("type", "array", "items", Map.of("type", "string")),
                        "atsCompatibility", Map.of("type", "string"),
                        "suggestions", Map.of("type", "array", "items", Map.of("type", "string")),
                        "recommendedChanges", Map.of("type", "array", "items", Map.of("type", "string"))
                ),
                "required", List.of(
                        "score", "summary", "strongestSkills", "missingOrWeakSkills",
                        "strengths", "weaknesses", "atsCompatibility", "suggestions", "recommendedChanges"
                ),
                "additionalProperties", false
        );
    }

    private Map<String, Object> buildImprovementJsonSchema() {
        Map<String, Object> bulletItem = Map.of(
                "type", "object",
                "properties", Map.of(
                        "section", Map.of("type", "string"),
                        "original", Map.of("type", "string"),
                        "improved", Map.of("type", "string"),
                        "explanation", Map.of("type", "string")
                ),
                "required", List.of("section", "original", "improved", "explanation"),
                "additionalProperties", false
        );

        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "improvedSummary", Map.of("type", "string"),
                        "bulletImprovements", Map.of("type", "array", "items", bulletItem),
                        "improvementExplanations", Map.of("type", "array", "items", Map.of("type", "string")),
                        "actionableChanges", Map.of("type", "array", "items", Map.of("type", "string"))
                ),
                "required", List.of("improvedSummary", "bulletImprovements", "improvementExplanations", "actionableChanges"),
                "additionalProperties", false
        );
    }
}
