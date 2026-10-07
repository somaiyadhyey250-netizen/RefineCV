package com.resumeanalyzer.resume_analyzer;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Centralized AI provider coordinator managing primary execution
 * and graceful fallback on rate limit, quota exhaustion, timeout, or service unavailability.
 */
@Service
@Primary
public class FallbackAIService implements AIProvider {

    private static final Logger logger = LoggerFactory.getLogger(FallbackAIService.class);

    private final AIProvider primaryProvider;
    private final AIProvider fallbackProvider;

    @Autowired
    public FallbackAIService(
            GeminiService geminiService,
            GroqAIProvider groqAIProvider,
            @Value("${refinecv.ai.primary-provider:gemini}") String preferredPrimary
    ) {
        if ("groq".equalsIgnoreCase(preferredPrimary)) {
            this.primaryProvider = Objects.requireNonNull(groqAIProvider, "groqAIProvider must not be null");
            this.fallbackProvider = Objects.requireNonNull(geminiService, "geminiService must not be null");
        } else {
            this.primaryProvider = Objects.requireNonNull(geminiService, "geminiService must not be null");
            this.fallbackProvider = Objects.requireNonNull(groqAIProvider, "groqAIProvider must not be null");
        }
        logger.info("FallbackAIService initialized with primary={} fallback={}",
                resolveProviderName(primaryProvider, "primary"),
                resolveProviderName(fallbackProvider, "fallback"));
    }

    public FallbackAIService(
            GeminiService geminiService,
            GroqAIProvider groqAIProvider
    ) {
        this(geminiService, groqAIProvider, "groq");
    }

    public FallbackAIService(
            AIProvider primaryProvider,
            AIProvider fallbackProvider
    ) {
        this.primaryProvider = Objects.requireNonNull(primaryProvider, "primaryProvider must not be null");
        this.fallbackProvider = Objects.requireNonNull(fallbackProvider, "fallbackProvider must not be null");
    }

    @Override
    public String getProviderName() {
        String name = primaryProvider.getProviderName();
        return (name != null && !name.isBlank()) ? name : "ai";
    }

    @Override
    public boolean isAvailable() {
        return primaryProvider.isAvailable() || fallbackProvider.isAvailable();
    }

    public AIProvider getPrimaryProvider() {
        return primaryProvider;
    }

    public AIProvider getFallbackProvider() {
        return fallbackProvider;
    }

    @Override
    public ResumeAnalysisDTO analyzeResume(String resumeText, Consumer<String> progress) {
        return analyzeResumeWithProvider(resumeText, progress).analysis();
    }

    @Override
    public ResumeAnalysisDTO analyzeResumeForJob(String resumeText, String jobDescription, Consumer<String> progress) {
        return analyzeResumeWithProvider(resumeText, AnalysisMode.SPECIFIC_JOB, jobDescription, progress).analysis();
    }

    @Override
    public AIAnalysisResult analyzeResumeWithProvider(String resumeText, Consumer<String> progress) {
        try {
            ResumeAnalysisDTO result = primaryProvider.analyzeResume(resumeText, progress);
            return new AIAnalysisResult(result, resolveProviderName(primaryProvider, "primary"));
        } catch (Exception e) {
            if (isFallbackEligible(e) && fallbackProvider.isAvailable()) {
                if (progress != null) {
                    progress.accept("ai-fallback");
                }
                String categoryName = (e instanceof AICommunicationException aiEx && aiEx.isRateLimited())
                        ? "RATE_QUOTA_EXHAUSTED" : "COMMUNICATION_FAILURE";
                logger.warn("provider={} operation=analysis fallback={} category={}",
                        resolveProviderName(primaryProvider, "primary"),
                        resolveProviderName(fallbackProvider, "fallback"),
                        categoryName);
                try {
                    ResumeAnalysisDTO fallbackResult = fallbackProvider.analyzeResume(resumeText, progress);
                    logger.info("provider={} operation=analysis success=true", resolveProviderName(fallbackProvider, "fallback"));
                    return new AIAnalysisResult(fallbackResult, resolveProviderName(fallbackProvider, "fallback"));
                } catch (Exception fallbackEx) {
                    logger.error("provider={} operation=analysis failed errorType={}",
                            resolveProviderName(fallbackProvider, "fallback"), fallbackEx.getClass().getName());
                    throw fallbackEx;
                }
            }
            throw e;
        }
    }

    @Override
    public AIAnalysisResult analyzeResumeWithProvider(String resumeText, AnalysisMode mode, String jobDescription, Consumer<String> progress) {
        if (mode != AnalysisMode.SPECIFIC_JOB || jobDescription == null || jobDescription.isBlank()) {
            return analyzeResumeWithProvider(resumeText, progress);
        }
        try {
            ResumeAnalysisDTO result = primaryProvider.analyzeResumeForJob(resumeText, jobDescription, progress);
            return new AIAnalysisResult(result, resolveProviderName(primaryProvider, "primary"));
        } catch (Exception e) {
            if (isFallbackEligible(e) && fallbackProvider.isAvailable()) {
                if (progress != null) {
                    progress.accept("ai-fallback");
                }
                String categoryName = (e instanceof AICommunicationException aiEx && aiEx.isRateLimited())
                        ? "RATE_QUOTA_EXHAUSTED" : "COMMUNICATION_FAILURE";
                logger.warn("provider={} operation=job_analysis fallback={} category={}",
                        resolveProviderName(primaryProvider, "primary"),
                        resolveProviderName(fallbackProvider, "fallback"),
                        categoryName);
                try {
                    ResumeAnalysisDTO fallbackResult = fallbackProvider.analyzeResumeForJob(resumeText, jobDescription, progress);
                    logger.info("provider={} operation=job_analysis success=true", resolveProviderName(fallbackProvider, "fallback"));
                    return new AIAnalysisResult(fallbackResult, resolveProviderName(fallbackProvider, "fallback"));
                } catch (Exception fallbackEx) {
                    logger.error("provider={} operation=job_analysis failed errorType={}",
                            resolveProviderName(fallbackProvider, "fallback"), fallbackEx.getClass().getName());
                    throw fallbackEx;
                }
            }
            throw e;
        }
    }

    @Override
    public ResumeImprovementDTO improveResume(String resumeText, ResumeAnalysisDTO analysis) {
        return improveResumeWithProvider(resumeText, analysis).improvement();
    }

    @Override
    public ResumeImprovementDTO improveResumeForJob(String resumeText, ResumeAnalysisDTO analysis, String jobDescription) {
        return improveResumeWithProvider(resumeText, analysis, jobDescription).improvement();
    }

    @Override
    public AIImprovementResult improveResumeWithProvider(String resumeText, ResumeAnalysisDTO analysis) {
        try {
            ResumeImprovementDTO result = primaryProvider.improveResume(resumeText, analysis);
            return new AIImprovementResult(result, resolveProviderName(primaryProvider, "primary"));
        } catch (Exception e) {
            if (isFallbackEligible(e) && fallbackProvider.isAvailable()) {
                String categoryName = (e instanceof AICommunicationException aiEx && aiEx.isRateLimited())
                        ? "RATE_QUOTA_EXHAUSTED" : "COMMUNICATION_FAILURE";
                logger.warn("provider={} operation=improvement fallback={} category={}",
                        resolveProviderName(primaryProvider, "primary"),
                        resolveProviderName(fallbackProvider, "fallback"),
                        categoryName);
                try {
                    ResumeImprovementDTO fallbackResult = fallbackProvider.improveResume(resumeText, analysis);
                    logger.info("provider={} operation=improvement success=true", resolveProviderName(fallbackProvider, "fallback"));
                    return new AIImprovementResult(fallbackResult, resolveProviderName(fallbackProvider, "fallback"));
                } catch (Exception fallbackEx) {
                    logger.error("provider={} operation=improvement failed errorType={}",
                            resolveProviderName(fallbackProvider, "fallback"), fallbackEx.getClass().getName());
                    throw fallbackEx;
                }
            }
            throw e;
        }
    }

    @Override
    public AIImprovementResult improveResumeWithProvider(String resumeText, ResumeAnalysisDTO analysis, String jobDescription) {
        if (jobDescription == null || jobDescription.isBlank()) {
            return improveResumeWithProvider(resumeText, analysis);
        }
        try {
            ResumeImprovementDTO result = primaryProvider.improveResumeForJob(resumeText, analysis, jobDescription);
            return new AIImprovementResult(result, resolveProviderName(primaryProvider, "primary"));
        } catch (Exception e) {
            if (isFallbackEligible(e) && fallbackProvider.isAvailable()) {
                String categoryName = (e instanceof AICommunicationException aiEx && aiEx.isRateLimited())
                        ? "RATE_QUOTA_EXHAUSTED" : "COMMUNICATION_FAILURE";
                logger.warn("provider={} operation=job_improvement fallback={} category={}",
                        resolveProviderName(primaryProvider, "primary"),
                        resolveProviderName(fallbackProvider, "fallback"),
                        categoryName);
                try {
                    ResumeImprovementDTO fallbackResult = fallbackProvider.improveResumeForJob(resumeText, analysis, jobDescription);
                    logger.info("provider={} operation=job_improvement success=true", resolveProviderName(fallbackProvider, "fallback"));
                    return new AIImprovementResult(fallbackResult, resolveProviderName(fallbackProvider, "fallback"));
                } catch (Exception fallbackEx) {
                    logger.error("provider={} operation=job_improvement failed errorType={}",
                            resolveProviderName(fallbackProvider, "fallback"), fallbackEx.getClass().getName());
                    throw fallbackEx;
                }
            }
            throw e;
        }
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
        return compareResumesWithProvider(resumeTextA, resumeTextB, jobDescription, comparisonId, fileNameA, fileNameB, progress).comparison();
    }

    @Override
    public AIComparisonResult compareResumesWithProvider(
            String resumeTextA,
            String resumeTextB,
            String jobDescription,
            String comparisonId,
            String fileNameA,
            String fileNameB,
            Consumer<String> progress
    ) {
        try {
            ResumeComparisonDTO result = primaryProvider.compareResumes(resumeTextA, resumeTextB, jobDescription, comparisonId, fileNameA, fileNameB, progress);
            return new AIComparisonResult(result, resolveProviderName(primaryProvider, "primary"));
        } catch (Exception e) {
            if (isFallbackEligible(e) && fallbackProvider.isAvailable()) {
                if (progress != null) {
                    progress.accept("ai-fallback");
                }
                String categoryName = (e instanceof AICommunicationException aiEx && aiEx.isRateLimited())
                        ? "RATE_QUOTA_EXHAUSTED" : "COMMUNICATION_FAILURE";
                logger.warn("provider={} operation=comparison fallback={} category={}",
                        resolveProviderName(primaryProvider, "primary"),
                        resolveProviderName(fallbackProvider, "fallback"),
                        categoryName);
                try {
                    ResumeComparisonDTO fallbackResult = fallbackProvider.compareResumes(resumeTextA, resumeTextB, jobDescription, comparisonId, fileNameA, fileNameB, progress);
                    logger.info("provider={} operation=comparison success=true", resolveProviderName(fallbackProvider, "fallback"));
                    return new AIComparisonResult(fallbackResult, resolveProviderName(fallbackProvider, "fallback"));
                } catch (Exception fallbackEx) {
                    logger.error("provider={} operation=comparison failed errorType={}",
                            resolveProviderName(fallbackProvider, "fallback"), fallbackEx.getClass().getName());
                    throw fallbackEx;
                }
            }
            throw e;
        }
    }

    @Override
    public InterviewPrepDTO generateInterviewPrep(
            String resumeText,
            String prepId,
            String filename,
            Consumer<String> progress
    ) {
        return generateInterviewPrepWithProvider(resumeText, prepId, filename, progress).prep();
    }

    @Override
    public AIInterviewPrepResult generateInterviewPrepWithProvider(
            String resumeText,
            String prepId,
            String filename,
            Consumer<String> progress
    ) {
        try {
            InterviewPrepDTO result = primaryProvider.generateInterviewPrep(resumeText, prepId, filename, progress);
            return new AIInterviewPrepResult(result, resolveProviderName(primaryProvider, "primary"));
        } catch (Exception e) {
            if (isFallbackEligible(e) && fallbackProvider.isAvailable()) {
                if (progress != null) {
                    progress.accept("ai-fallback");
                }
                String categoryName = (e instanceof AICommunicationException aiEx && aiEx.isRateLimited())
                        ? "RATE_QUOTA_EXHAUSTED" : "COMMUNICATION_FAILURE";
                logger.warn("provider={} operation=interview_prep fallback={} category={}",
                        resolveProviderName(primaryProvider, "primary"),
                        resolveProviderName(fallbackProvider, "fallback"),
                        categoryName);
                try {
                    InterviewPrepDTO fallbackResult = fallbackProvider.generateInterviewPrep(resumeText, prepId, filename, progress);
                    logger.info("provider={} operation=interview_prep success=true", resolveProviderName(fallbackProvider, "fallback"));
                    return new AIInterviewPrepResult(fallbackResult, resolveProviderName(fallbackProvider, "fallback"));
                } catch (Exception fallbackEx) {
                    logger.error("provider={} operation=interview_prep failed errorType={}",
                            resolveProviderName(fallbackProvider, "fallback"), fallbackEx.getClass().getName());
                    throw fallbackEx;
                }
            }
            throw e;
        }
    }

    @Override
    public List<InterviewQuestionDTO> generateMoreQuestions(
            String resumeText,
            List<String> existingQuestions
    ) {
        try {
            return primaryProvider.generateMoreQuestions(resumeText, existingQuestions);
        } catch (Exception e) {
            if (isFallbackEligible(e) && fallbackProvider.isAvailable()) {
                String categoryName = (e instanceof AICommunicationException aiEx && aiEx.isRateLimited())
                        ? "RATE_QUOTA_EXHAUSTED" : "COMMUNICATION_FAILURE";
                logger.warn("provider={} operation=generate_more_questions fallback={} category={}",
                        resolveProviderName(primaryProvider, "primary"),
                        resolveProviderName(fallbackProvider, "fallback"),
                        categoryName);
                try {
                    List<InterviewQuestionDTO> fallbackResult = fallbackProvider.generateMoreQuestions(resumeText, existingQuestions);
                    logger.info("provider={} operation=generate_more_questions success=true", resolveProviderName(fallbackProvider, "fallback"));
                    return fallbackResult;
                } catch (Exception fallbackEx) {
                    logger.error("provider={} operation=generate_more_questions failed errorType={}",
                            resolveProviderName(fallbackProvider, "fallback"), fallbackEx.getClass().getName());
                    throw fallbackEx;
                }
            }
            throw e;
        }
    }

    @Override
    public InterviewAnswerEvaluationDTO evaluateAnswer(
            String question,
            String basedOn,
            String userAnswer
    ) {
        return evaluateAnswerWithProvider(question, basedOn, userAnswer).evaluation();
    }

    @Override
    public AIAnswerEvaluationResult evaluateAnswerWithProvider(
            String question,
            String basedOn,
            String userAnswer
    ) {
        try {
            InterviewAnswerEvaluationDTO result = primaryProvider.evaluateAnswer(question, basedOn, userAnswer);
            return new AIAnswerEvaluationResult(result, resolveProviderName(primaryProvider, "primary"));
        } catch (Exception e) {
            if (isFallbackEligible(e) && fallbackProvider.isAvailable()) {
                String categoryName = (e instanceof AICommunicationException aiEx && aiEx.isRateLimited())
                        ? "RATE_QUOTA_EXHAUSTED" : "COMMUNICATION_FAILURE";
                logger.warn("provider={} operation=evaluate_answer fallback={} category={}",
                        resolveProviderName(primaryProvider, "primary"),
                        resolveProviderName(fallbackProvider, "fallback"),
                        categoryName);
                try {
                    InterviewAnswerEvaluationDTO fallbackResult = fallbackProvider.evaluateAnswer(question, basedOn, userAnswer);
                    logger.info("provider={} operation=evaluate_answer success=true", resolveProviderName(fallbackProvider, "fallback"));
                    return new AIAnswerEvaluationResult(fallbackResult, resolveProviderName(fallbackProvider, "fallback"));
                } catch (Exception fallbackEx) {
                    logger.error("provider={} operation=evaluate_answer failed errorType={}",
                            resolveProviderName(fallbackProvider, "fallback"), fallbackEx.getClass().getName());
                    throw fallbackEx;
                }
            }
            throw e;
        }
    }

    private String resolveProviderName(AIProvider provider, String defaultName) {
        if (provider == null) return defaultName;
        String name = provider.getProviderName();
        return (name != null && !name.isBlank()) ? name : defaultName;
    }

    private boolean isFallbackEligible(Throwable error) {
        if (error instanceof CancellationException || Thread.currentThread().isInterrupted()) {
            return false;
        }
        if (error instanceof AnalysisContractValidationException || error instanceof GeminiResponseException) {
            return false;
        }
        if (error instanceof AICommunicationException aiEx) {
            return aiEx.isFallbackEligible();
        }
        return false;
    }
}
