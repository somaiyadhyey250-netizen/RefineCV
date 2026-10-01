package com.resumeanalyzer.resume_analyzer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Request payload for V4.1 resume improvement endpoint. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResumeImprovementRequest(
        String analysisId,
        String resumeText,
        ResumeAnalysisDTO analysis,
        String jobDescription
) {
    public ResumeImprovementRequest(String analysisId, String resumeText, ResumeAnalysisDTO analysis) {
        this(analysisId, resumeText, analysis, null);
    }
}
