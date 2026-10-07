package com.resumeanalyzer.resume_analyzer;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.google.genai.types.Schema;

@Service
public class GeminiService implements AIProvider {

    private static final Logger logger = LoggerFactory.getLogger(GeminiService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final int maxResponseCharacters;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final ExecutorService geminiExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "gemini-deadline-worker");
        t.setDaemon(true);
        return t;
    });

    private volatile Client cachedClient;

    @Autowired
    public GeminiService(
            @Value("${refinecv.gemini.max-response-characters:20000}") int maxResponseCharacters,
            @Value("${refinecv.gemini.api-key:}") String apiKey,
            @Value("${refinecv.gemini.model:gemini-3.6-flash}") String model,
            @Value("${refinecv.gemini.timeout:15s}") Duration timeout
    ) {
        this.maxResponseCharacters = maxResponseCharacters;
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = (model == null || model.isBlank()) ? "gemini-3.6-flash" : model.trim();
        this.timeout = timeout != null ? timeout : Duration.ofSeconds(15);
    }

    public GeminiService(
            int maxResponseCharacters,
            String apiKey,
            String model
    ) {
        this(maxResponseCharacters, apiKey, model, Duration.ofSeconds(15));
    }

    public GeminiService(
            int maxResponseCharacters,
            String apiKey
    ) {
        this(maxResponseCharacters, apiKey, "gemini-3.6-flash", Duration.ofSeconds(15));
    }

    @Override
    public String getProviderName() {
        return "gemini";
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty();
    }

    public Duration getTimeout() {
        return timeout;
    }

    private synchronized Client getOrCreateClient() {
        if (cachedClient != null) {
            return cachedClient;
        }
        try {
            HttpOptions httpOptions = HttpOptions.builder()
                    .timeout((int) timeout.toMillis())
                    .retryOptions(HttpRetryOptions.builder()
                            .attempts(1)
                            .build())
                    .build();
            cachedClient = Client.builder()
                    .apiKey(apiKey)
                    .httpOptions(httpOptions)
                    .build();
            return cachedClient;
        } catch (RuntimeException e) {
            throw new GeminiCommunicationException("Could not initialize Gemini client.", e);
        }
    }

    GenerateContentResponse callGeminiWithDeadline(
            String prompt,
            GenerateContentConfig config,
            String operation
    ) {
        if (!isAvailable()) {
            throw new GeminiCommunicationException("Gemini is not configured.", null);
        }

        Client client = getOrCreateClient();
        long startTime = System.currentTimeMillis();

        Future<GenerateContentResponse> future = geminiExecutor.submit(() ->
                client.models.generateContent(model, prompt, config)
        );

        try {
            GenerateContentResponse response = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("provider=gemini operation={} status=200 durationMs={}", operation, elapsed);
            return response;
        } catch (TimeoutException e) {
            future.cancel(true);
            long elapsed = System.currentTimeMillis() - startTime;
            logger.warn("provider=gemini operation={} status=timeout durationMs={} timeoutLimitMs={}",
                    operation, elapsed, timeout.toMillis());
            throw new GeminiCommunicationException(
                    "Gemini " + operation + " request timed out after " + timeout.toSeconds() + " seconds.",
                    e,
                    false
            );
        } catch (ExecutionException e) {
            future.cancel(true);
            long elapsed = System.currentTimeMillis() - startTime;
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            boolean isRateLimit = isRateLimitError(cause);
            logger.warn("provider=gemini operation={} status=failed rateLimit={} durationMs={} error={}",
                    operation, isRateLimit, elapsed, cause.getMessage());
            throw new GeminiCommunicationException(
                    isRateLimit ? "Gemini quota or rate limit exceeded." : "Gemini " + operation + " request failed.",
                    cause,
                    isRateLimit
            );
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new CancellationException("Gemini " + operation + " was interrupted.");
        }
    }

    public ResumeAnalysisDTO analyzeResume(
            String resumeText
    ) {
        return analyzeResume(
                resumeText,
                status -> {}
        );
    }

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
        Schema resumeSchema = buildResumeSchema(mode);
        String prompt = (mode == AnalysisMode.SPECIFIC_JOB && jobDescription != null && !jobDescription.isBlank())
                ? AIPromptBuilder.buildJobAnalysisPrompt(resumeText, jobDescription)
                : buildPrompt(resumeText);

        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(resumeSchema)
                .build();

        if (progress != null) {
            progress.accept("ai-analysis");
        }

        GenerateContentResponse response = callGeminiWithDeadline(prompt, config, "analysis");

        if (progress != null) {
            progress.accept("recommendations");
        }

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

        GenerateContentResponse response = callGeminiWithDeadline(prompt, config, "improvement");

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

        GenerateContentResponse response = callGeminiWithDeadline(prompt, config, "comparison");

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

    @Override
    public InterviewPrepDTO generateInterviewPrep(
            String resumeText,
            String prepId,
            String filename,
            Consumer<String> progress
    ) {
        Schema questionSchema = Schema.builder()
                .type("OBJECT")
                .properties(Map.of(
                        "question", Schema.builder().type("STRING").build(),
                        "questionType", Schema.builder().type("STRING").build(),
                        "riskLevel", Schema.builder().type("STRING").build(),
                        "basedOn", Schema.builder().type("STRING").build(),
                        "interviewerIntent", Schema.builder().type("STRING").build(),
                        "preparationHint", Schema.builder().type("STRING").build()
                ))
                .required(List.of("question", "questionType", "riskLevel", "basedOn", "interviewerIntent", "preparationHint"))
                .build();

        Schema claimSchema = Schema.builder()
                .type("OBJECT")
                .properties(Map.of(
                        "claim", Schema.builder().type("STRING").build(),
                        "riskLevel", Schema.builder().type("STRING").build(),
                        "preparationNote", Schema.builder().type("STRING").build()
                ))
                .required(List.of("claim", "riskLevel", "preparationNote"))
                .build();

        Schema prepSchema = Schema.builder()
                .type("OBJECT")
                .properties(Map.of(
                        "questions", Schema.builder().type("ARRAY").items(questionSchema).build(),
                        "claimsToPrepare", Schema.builder().type("ARRAY").items(claimSchema).build(),
                        "overallPreparationNote", Schema.builder().type("STRING").build()
                ))
                .required(List.of("questions", "claimsToPrepare", "overallPreparationNote"))
                .build();

        String prompt = AIPromptBuilder.buildInterviewPrepPrompt(resumeText);

        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(prepSchema)
                .build();

        if (progress != null) {
            progress.accept("generating");
        }

        GenerateContentResponse response = callGeminiWithDeadline(prompt, config, "interview_prep");

        String result = response == null ? null : response.text();
        return AIResponseParser.parseAndValidateInterviewPrep(
                result,
                prepId,
                filename,
                maxResponseCharacters,
                objectMapper,
                "gemini"
        );
    }

    @Override
    public List<InterviewQuestionDTO> generateMoreQuestions(
            String resumeText,
            List<String> existingQuestions
    ) {
        Schema questionSchema = Schema.builder()
                .type("OBJECT")
                .properties(Map.of(
                        "question", Schema.builder().type("STRING").build(),
                        "questionType", Schema.builder().type("STRING").build(),
                        "riskLevel", Schema.builder().type("STRING").build(),
                        "basedOn", Schema.builder().type("STRING").build(),
                        "interviewerIntent", Schema.builder().type("STRING").build(),
                        "preparationHint", Schema.builder().type("STRING").build()
                ))
                .required(List.of("question", "questionType", "riskLevel", "basedOn", "interviewerIntent", "preparationHint"))
                .build();

        Schema additionalSchema = Schema.builder()
                .type("OBJECT")
                .properties(Map.of(
                        "questions", Schema.builder().type("ARRAY").items(questionSchema).build()
                ))
                .required(List.of("questions"))
                .build();

        String prompt = AIPromptBuilder.buildGenerateMoreQuestionsPrompt(resumeText, existingQuestions);

        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(additionalSchema)
                .build();

        GenerateContentResponse response = callGeminiWithDeadline(prompt, config, "generate_more_questions");

        String result = response == null ? null : response.text();
        return AIResponseParser.parseAndValidateAdditionalQuestions(
                result,
                maxResponseCharacters,
                objectMapper,
                "gemini"
        );
    }

    @Override
    public InterviewAnswerEvaluationDTO evaluateAnswer(
            String question,
            String basedOn,
            String userAnswer
    ) {
        Schema evalSchema = Schema.builder()
                .type("OBJECT")
                .properties(Map.of(
                        "answerQuality", Schema.builder().type("STRING").build(),
                        "strengths", Schema.builder().type("ARRAY").items(Schema.builder().type("STRING").build()).build(),
                        "improvements", Schema.builder().type("ARRAY").items(Schema.builder().type("STRING").build()).build()
                ))
                .required(List.of("answerQuality", "strengths", "improvements"))
                .build();

        String prompt = AIPromptBuilder.buildAnswerEvaluationPrompt(question, basedOn, userAnswer);

        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(evalSchema)
                .build();

        GenerateContentResponse response = callGeminiWithDeadline(prompt, config, "answer_evaluation");

        String result = response == null ? null : response.text();
        return AIResponseParser.parseAndValidateAnswerEvaluation(
                result,
                "evaluated",
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
