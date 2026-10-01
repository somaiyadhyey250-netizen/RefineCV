package com.resumeanalyzer.resume_analyzer;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Centralized AI provider coordinator managing primary (Gemini) execution
 * and graceful fallback (Groq) on rate limit, quota exhaustion, or service unavailability.
 */
@Service
@Primary
public class FallbackAIService implements AIProvider {

    private static final Logger logger = LoggerFactory.getLogger(FallbackAIService.class);

    private final AIProvider primaryProvider;
    private final AIProvider fallbackProvider;

    @Autowired
    public FallbackAIService(
            GeminiService primaryProvider,
            GroqAIProvider fallbackProvider
    ) {
        this((AIProvider) primaryProvider, (AIProvider) fallbackProvider);
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
        return (name != null && !name.isBlank()) ? name : "gemini";
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
    public AIAnalysisResult analyzeResumeWithProvider(String resumeText, Consumer<String> progress) {
        try {
            ResumeAnalysisDTO result = primaryProvider.analyzeResume(resumeText, progress);
            String providerName = primaryProvider.getProviderName();
            if (providerName == null || providerName.isBlank()) {
                providerName = "gemini";
            }
            return new AIAnalysisResult(result, providerName);
        } catch (Exception e) {
            if (isFallbackEligible(e) && fallbackProvider.isAvailable()) {
                String categoryName = (e instanceof AICommunicationException aiEx && aiEx.isRateLimited())
                        ? "RATE_QUOTA_EXHAUSTED" : "COMMUNICATION_FAILURE";
                logger.warn("provider={} operation=analysis fallback={} category={}",
                        resolveProviderName(primaryProvider, "gemini"),
                        resolveProviderName(fallbackProvider, "groq"),
                        categoryName);
                try {
                    ResumeAnalysisDTO fallbackResult = fallbackProvider.analyzeResume(resumeText, progress);
                    logger.info("provider={} operation=analysis success=true", resolveProviderName(fallbackProvider, "groq"));
                    return new AIAnalysisResult(fallbackResult, resolveProviderName(fallbackProvider, "groq"));
                } catch (Exception fallbackEx) {
                    logger.error("provider={} operation=analysis failed errorType={}",
                            resolveProviderName(fallbackProvider, "groq"), fallbackEx.getClass().getName());
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
    public AIImprovementResult improveResumeWithProvider(String resumeText, ResumeAnalysisDTO analysis) {
        try {
            ResumeImprovementDTO result = primaryProvider.improveResume(resumeText, analysis);
            String providerName = primaryProvider.getProviderName();
            if (providerName == null || providerName.isBlank()) {
                providerName = "gemini";
            }
            return new AIImprovementResult(result, providerName);
        } catch (Exception e) {
            if (isFallbackEligible(e) && fallbackProvider.isAvailable()) {
                String categoryName = (e instanceof AICommunicationException aiEx && aiEx.isRateLimited())
                        ? "RATE_QUOTA_EXHAUSTED" : "COMMUNICATION_FAILURE";
                logger.warn("provider={} operation=improvement fallback={} category={}",
                        resolveProviderName(primaryProvider, "gemini"),
                        resolveProviderName(fallbackProvider, "groq"),
                        categoryName);
                try {
                    ResumeImprovementDTO fallbackResult = fallbackProvider.improveResume(resumeText, analysis);
                    logger.info("provider={} operation=improvement success=true", resolveProviderName(fallbackProvider, "groq"));
                    return new AIImprovementResult(fallbackResult, resolveProviderName(fallbackProvider, "groq"));
                } catch (Exception fallbackEx) {
                    logger.error("provider={} operation=improvement failed errorType={}",
                            resolveProviderName(fallbackProvider, "groq"), fallbackEx.getClass().getName());
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
