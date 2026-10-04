package com.resumeanalyzer.resume_analyzer;

/**
 * Encapsulates an interview answer evaluation alongside the provider that generated it.
 */
public record AIAnswerEvaluationResult(
        InterviewAnswerEvaluationDTO evaluation,
        String providerName
) {
}
