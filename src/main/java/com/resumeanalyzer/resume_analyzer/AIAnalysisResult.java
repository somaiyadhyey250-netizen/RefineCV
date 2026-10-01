package com.resumeanalyzer.resume_analyzer;

/** Carries the analysis DTO along with safe provider observability metadata. */
public record AIAnalysisResult(ResumeAnalysisDTO analysis, String providerName) {
}
