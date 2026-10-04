package com.resumeanalyzer.resume_analyzer;

/**
 * Encapsulates an interview prep DTO alongside the AI provider name that produced it.
 */
public record AIInterviewPrepResult(
        InterviewPrepDTO prep,
        String providerName
) {
}
