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

    /** Performs job-specific resume analysis evaluating the resume against the target job description. */
    default ResumeAnalysisDTO analyzeResumeForJob(String resumeText, String jobDescription, Consumer<String> progress) {
        return analyzeResume(resumeText, progress);
    }

    /** Performs job-targeted resume improvement tailored to the specific role requirements without inventing facts. */
    default ResumeImprovementDTO improveResumeForJob(String resumeText, ResumeAnalysisDTO analysis, String jobDescription) {
        return improveResume(resumeText, analysis);
    }

    /** Performs resume analysis and returns the result along with the provider name that generated it. */
    default AIAnalysisResult analyzeResumeWithProvider(String resumeText, Consumer<String> progress) {
        ResumeAnalysisDTO dto = analyzeResume(resumeText, progress);
        return new AIAnalysisResult(dto, getProviderName());
    }

    /** Performs resume analysis for a specific mode and returns the result with provider metadata. */
    default AIAnalysisResult analyzeResumeWithProvider(String resumeText, AnalysisMode mode, String jobDescription, Consumer<String> progress) {
        if (mode == AnalysisMode.SPECIFIC_JOB && jobDescription != null && !jobDescription.isBlank()) {
            ResumeAnalysisDTO dto = analyzeResumeForJob(resumeText, jobDescription, progress);
            return new AIAnalysisResult(dto, getProviderName());
        }
        return analyzeResumeWithProvider(resumeText, progress);
    }

    /** Performs resume improvement and returns the result along with the provider name that generated it. */
    default AIImprovementResult improveResumeWithProvider(String resumeText, ResumeAnalysisDTO analysis) {
        ResumeImprovementDTO dto = improveResume(resumeText, analysis);
        return new AIImprovementResult(dto, getProviderName());
    }

    /** Performs resume improvement tailored to an optional target job description. */
    default AIImprovementResult improveResumeWithProvider(String resumeText, ResumeAnalysisDTO analysis, String jobDescription) {
        if (jobDescription != null && !jobDescription.isBlank()) {
            ResumeImprovementDTO dto = improveResumeForJob(resumeText, analysis, jobDescription);
            return new AIImprovementResult(dto, getProviderName());
        }
        return improveResumeWithProvider(resumeText, analysis);
    }
}
