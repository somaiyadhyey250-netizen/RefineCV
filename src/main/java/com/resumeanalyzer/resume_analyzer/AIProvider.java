package com.resumeanalyzer.resume_analyzer;

import java.util.function.Consumer;

/** Common contract implemented by AI providers (Gemini, Groq) and coordinating services. */
public interface AIProvider {

    /** Returns the unique identifier of this provider (e.g. "gemini", "groq"). */
    String getProviderName();

    /** Indicates whether this provider is configured and available for requests. */
    boolean isAvailable();

    /** Performs resume analysis, reporting intermediate progress stages to the consumer. */
    ResumeAnalysisDTO analyzeResume(String resumeText, Consumer<String> progress);

    /** Performs AI-guided resume improvement grounded strictly on the uploaded resume and prior analysis. */
    ResumeImprovementDTO improveResume(String resumeText, ResumeAnalysisDTO analysis);

    /** Performs resume analysis and returns the result along with the provider name that generated it. */
    default AIAnalysisResult analyzeResumeWithProvider(String resumeText, Consumer<String> progress) {
        ResumeAnalysisDTO dto = analyzeResume(resumeText, progress);
        return new AIAnalysisResult(dto, getProviderName());
    }

    /** Performs resume improvement and returns the result along with the provider name that generated it. */
    default AIImprovementResult improveResumeWithProvider(String resumeText, ResumeAnalysisDTO analysis) {
        ResumeImprovementDTO dto = improveResume(resumeText, analysis);
        return new AIImprovementResult(dto, getProviderName());
    }
}
