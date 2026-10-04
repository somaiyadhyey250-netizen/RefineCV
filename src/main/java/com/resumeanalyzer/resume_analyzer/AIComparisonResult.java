package com.resumeanalyzer.resume_analyzer;

/** Metadata wrapper bundling the comparison DTO and the resolving AI provider name. */
public record AIComparisonResult(
        ResumeComparisonDTO comparison,
        String providerName
) {
}
