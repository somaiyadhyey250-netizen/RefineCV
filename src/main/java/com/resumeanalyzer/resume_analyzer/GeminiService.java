package com.resumeanalyzer.resume_analyzer;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Schema;

@Service
public class GeminiService implements AIProvider {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final int maxResponseCharacters;
    private final String apiKey;
    private final String model;

    @Autowired
    public GeminiService(
            @Value("${refinecv.gemini.max-response-characters:20000}") int maxResponseCharacters,
            @Value("${refinecv.gemini.api-key:}") String apiKey,
            @Value("${refinecv.gemini.model:gemini-3.6-flash}") String model
    ) {
        this.maxResponseCharacters = maxResponseCharacters;
        this.apiKey = apiKey;
        this.model = (model == null || model.isBlank()) ? "gemini-3.6-flash" : model;
    }

    public GeminiService(
            int maxResponseCharacters,
            String apiKey
    ) {
        this(maxResponseCharacters, apiKey, "gemini-3.6-flash");
    }

    @Override
    public String getProviderName() {
        return "gemini";
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    /*
     * Existing method.
     * Keeps compatibility with any code that calls analyzeResume(resumeText).
     */
    public ResumeAnalysisDTO analyzeResume(
            String resumeText
    ) {
        return analyzeResume(
                resumeText,
                status -> {
                    // No progress listener required.
                }
        );
    }

    /*
     * Method with progress reporting.
     */
    @Override
    public ResumeAnalysisDTO analyzeResume(
            String resumeText,
            Consumer<String> progress
    ) {
        if (!isAvailable()) {
            throw new GeminiCommunicationException("Gemini is not configured.", null);
        }

        Client client;
        try {
            client = Client.builder().apiKey(apiKey).build();
        } catch (RuntimeException e) {
            throw new GeminiCommunicationException("Could not initialize Gemini client.", e);
        }

        Schema resumeSchema = Schema.builder()
                .type("OBJECT")
                .properties(
                        Map.of(
                                "score", Schema.builder().type("INTEGER").build(),
                                "summary", Schema.builder().type("STRING").build(),
                                "strongestSkills", Schema.builder().type("ARRAY")
                                        .items(Schema.builder().type("STRING").build()).build(),
                                "missingOrWeakSkills", Schema.builder().type("ARRAY")
                                        .items(Schema.builder().type("STRING").build()).build(),
                                "strengths", Schema.builder().type("ARRAY")
                                        .items(Schema.builder().type("STRING").build()).build(),
                                "weaknesses", Schema.builder().type("ARRAY")
                                        .items(Schema.builder().type("STRING").build()).build(),
                                "atsCompatibility", Schema.builder().type("STRING").build(),
                                "suggestions", Schema.builder().type("ARRAY")
                                        .items(Schema.builder().type("STRING").build()).build(),
                                "recommendedChanges", Schema.builder().type("ARRAY")
                                        .items(Schema.builder().type("STRING").build()).build()
                        )
                )
                .required(
                        List.of(
                                "score", "summary", "strongestSkills", "missingOrWeakSkills",
                                "strengths", "weaknesses", "atsCompatibility", "suggestions", "recommendedChanges"
                        )
                )
                .build();

        String prompt = buildPrompt(resumeText);

        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(resumeSchema)
                .build();

        progress.accept("ai-analysis");

        GenerateContentResponse response;
        try {
            response = client.models.generateContent(
                    model,
                    prompt,
                    config
            );
        } catch (RuntimeException e) {
            boolean isRateLimit = isRateLimitError(e);
            throw new GeminiCommunicationException(
                    isRateLimit ? "Gemini quota or rate limit exceeded." : "Gemini analysis request failed.",
                    e,
                    isRateLimit
            );
        }

        progress.accept("recommendations");

        String result = response == null ? null : response.text();
        return parseAndValidateResponse(result);
    }

    static String buildPrompt(String resumeText) {
        return AIPromptBuilder.buildAnalysisPrompt(resumeText);
    }

    ResumeAnalysisDTO parseAndValidateResponse(String rawResponse) {
        return AIResponseParser.parseAndValidateAnalysis(rawResponse, maxResponseCharacters, objectMapper, "gemini");
    }

    /* =========================================================
       V4.1 AI RESUME IMPROVEMENT OPERATION
       ========================================================= */

    @Override
    public ResumeImprovementDTO improveResume(
            String resumeText,
            ResumeAnalysisDTO analysis
    ) {
        if (!isAvailable()) {
            throw new GeminiCommunicationException("Gemini is not configured.", null);
        }

        Client client;
        try {
            client = Client.builder().apiKey(apiKey).build();
        } catch (RuntimeException e) {
            throw new GeminiCommunicationException("Could not initialize Gemini client.", e);
        }

        Schema bulletItemSchema = Schema.builder()
                .type("OBJECT")
                .properties(Map.of(
                        "section", Schema.builder().type("STRING").build(),
                        "original", Schema.builder().type("STRING").build(),
                        "improved", Schema.builder().type("STRING").build(),
                        "explanation", Schema.builder().type("STRING").build()
                ))
                .required(List.of("section", "original", "improved", "explanation"))
                .build();

        Schema improvementSchema = Schema.builder()
                .type("OBJECT")
                .properties(Map.of(
                        "improvedSummary", Schema.builder().type("STRING").build(),
                        "bulletImprovements", Schema.builder()
                                .type("ARRAY")
                                .items(bulletItemSchema)
                                .build(),
                        "improvementExplanations", Schema.builder()
                                .type("ARRAY")
                                .items(Schema.builder().type("STRING").build())
                                .build(),
                        "actionableChanges", Schema.builder()
                                .type("ARRAY")
                                .items(Schema.builder().type("STRING").build())
                                .build()
                ))
                .required(List.of(
                        "improvedSummary",
                        "bulletImprovements",
                        "improvementExplanations",
                        "actionableChanges"
                ))
                .build();

        String prompt = buildImprovementPrompt(resumeText, analysis);

        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(improvementSchema)
                .build();

        GenerateContentResponse response;
        try {
            response = client.models.generateContent(
                    model,
                    prompt,
                    config
            );
        } catch (RuntimeException e) {
            boolean isRateLimit = isRateLimitError(e);
            throw new GeminiCommunicationException(
                    isRateLimit ? "Gemini quota or rate limit exceeded." : "Gemini improvement request failed.",
                    e,
                    isRateLimit
            );
        }

        String result = response == null ? null : response.text();
        return parseAndValidateImprovementResponse(result);
    }

    static String buildImprovementPrompt(String resumeText, ResumeAnalysisDTO analysis) {
        return AIPromptBuilder.buildImprovementPrompt(resumeText, analysis);
    }

    ResumeImprovementDTO parseAndValidateImprovementResponse(String rawResponse) {
        return AIResponseParser.parseAndValidateImprovement(rawResponse, maxResponseCharacters, objectMapper, "gemini");
    }

    private static boolean isRateLimitError(Throwable e) {
        if (e == null) return false;
        String message = e.getMessage();
        if (message != null) {
            String lower = message.toLowerCase();
            if (lower.contains("429") || lower.contains("resource_exhausted") || lower.contains("quota exceeded")) {
                return true;
            }
        }
        return isRateLimitError(e.getCause());
    }
}
