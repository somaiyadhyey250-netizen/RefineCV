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
        return executeAnalysis(resumeText, AnalysisMode.GENERAL, null, progress);
    }

    @Override
    public ResumeAnalysisDTO analyzeResumeForJob(
            String resumeText,
            String jobDescription,
            Consumer<String> progress
    ) {
        return executeAnalysis(resumeText, AnalysisMode.SPECIFIC_JOB, jobDescription, progress);
    }

    private ResumeAnalysisDTO executeAnalysis(
            String resumeText,
            AnalysisMode mode,
            String jobDescription,
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

        Schema resumeSchema = buildResumeSchema(mode);
        String prompt = (mode == AnalysisMode.SPECIFIC_JOB && jobDescription != null && !jobDescription.isBlank())
                ? AIPromptBuilder.buildJobAnalysisPrompt(resumeText, jobDescription)
                : buildPrompt(resumeText);

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

    private Schema buildResumeSchema(AnalysisMode mode) {
        var properties = new java.util.HashMap<String, Schema>();
        properties.put("score", Schema.builder().type("INTEGER").build());
        properties.put("summary", Schema.builder().type("STRING").build());
        properties.put("strongestSkills", Schema.builder().type("ARRAY")
                .items(Schema.builder().type("STRING").build()).build());
        properties.put("missingOrWeakSkills", Schema.builder().type("ARRAY")
                .items(Schema.builder().type("STRING").build()).build());
        properties.put("strengths", Schema.builder().type("ARRAY")
                .items(Schema.builder().type("STRING").build()).build());
        properties.put("weaknesses", Schema.builder().type("ARRAY")
                .items(Schema.builder().type("STRING").build()).build());
        properties.put("atsCompatibility", Schema.builder().type("STRING").build());
        properties.put("suggestions", Schema.builder().type("ARRAY")
                .items(Schema.builder().type("STRING").build()).build());
        properties.put("recommendedChanges", Schema.builder().type("ARRAY")
                .items(Schema.builder().type("STRING").build()).build());

        var required = new java.util.ArrayList<>(List.of(
                "score", "summary", "strongestSkills", "missingOrWeakSkills",
                "strengths", "weaknesses", "atsCompatibility", "suggestions", "recommendedChanges"
        ));

        if (mode == AnalysisMode.SPECIFIC_JOB) {
            properties.put("analysisMode", Schema.builder().type("STRING").build());
            properties.put("jobMatchScore", Schema.builder().type("INTEGER").build());
            properties.put("keywordAlignment", Schema.builder().type("STRING").build());
            properties.put("experienceAlignment", Schema.builder().type("STRING").build());
            required.addAll(List.of("analysisMode", "jobMatchScore", "keywordAlignment", "experienceAlignment"));
        }

        return Schema.builder()
                .type("OBJECT")
                .properties(properties)
                .required(required)
                .build();
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
        return executeImprovement(resumeText, analysis, null);
    }

    @Override
    public ResumeImprovementDTO improveResumeForJob(
            String resumeText,
            ResumeAnalysisDTO analysis,
            String jobDescription
    ) {
        return executeImprovement(resumeText, analysis, jobDescription);
    }

    private ResumeImprovementDTO executeImprovement(
            String resumeText,
            ResumeAnalysisDTO analysis,
            String jobDescription
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

        String prompt = (jobDescription != null && !jobDescription.isBlank())
                ? AIPromptBuilder.buildJobImprovementPrompt(resumeText, analysis, jobDescription)
                : buildImprovementPrompt(resumeText, analysis);

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

    @Override
    public ResumeComparisonDTO compareResumes(
            String resumeTextA,
            String resumeTextB,
            String jobDescription,
            String comparisonId,
            String fileNameA,
            String fileNameB,
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

        Schema categorySchema = Schema.builder()
                .type("OBJECT")
                .properties(Map.of(
                        "categoryId", Schema.builder().type("STRING").build(),
                        "name", Schema.builder().type("STRING").build(),
                        "maxPoints", Schema.builder().type("INTEGER").build(),
                        "scoreA", Schema.builder().type("INTEGER").build(),
                        "scoreB", Schema.builder().type("INTEGER").build(),
                        "evidenceA", Schema.builder().type("STRING").build(),
                        "evidenceB", Schema.builder().type("STRING").build(),
                        "explanationA", Schema.builder().type("STRING").build(),
                        "explanationB", Schema.builder().type("STRING").build()
                ))
                .required(List.of("categoryId", "name", "maxPoints", "scoreA", "scoreB", "evidenceA", "evidenceB", "explanationA", "explanationB"))
                .build();

        Schema comparisonSchema = Schema.builder()
                .type("OBJECT")
                .properties(Map.of(
                        "jobContext", Schema.builder().type("STRING").build(),
                        "categories", Schema.builder()
                                .type("ARRAY")
                                .items(categorySchema)
                                .build(),
                        "keyDifferentiators", Schema.builder()
                                .type("ARRAY")
                                .items(Schema.builder().type("STRING").build())
                                .build(),
                        "overallTakeaway", Schema.builder().type("STRING").build(),
                        "resumeABorrowsFromB", Schema.builder()
                                .type("ARRAY")
                                .items(Schema.builder().type("STRING").build())
                                .build(),
                        "resumeBBorrowsFromA", Schema.builder()
                                .type("ARRAY")
                                .items(Schema.builder().type("STRING").build())
                                .build()
                ))
                .required(List.of(
                        "categories",
                        "keyDifferentiators",
                        "overallTakeaway",
                        "resumeABorrowsFromB",
                        "resumeBBorrowsFromA"
                ))
                .build();

        String prompt = AIPromptBuilder.buildComparisonPrompt(resumeTextA, resumeTextB, jobDescription);

        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(comparisonSchema)
                .build();

        if (progress != null) {
            progress.accept("comparing");
        }

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
                    isRateLimit ? "Gemini quota or rate limit exceeded." : "Gemini comparison request failed.",
                    e,
                    isRateLimit
            );
        }

        String result = response == null ? null : response.text();
        return AIResponseParser.parseAndValidateComparison(
                result,
                comparisonId,
                fileNameA,
                fileNameB,
                jobDescription,
                maxResponseCharacters,
                objectMapper,
                "gemini"
        );
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
