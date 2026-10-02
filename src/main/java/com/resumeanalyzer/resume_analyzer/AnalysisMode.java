package com.resumeanalyzer.resume_analyzer;

/**
 * Supported resume analysis modes for RefineCV.
 */
public enum AnalysisMode {
    GENERAL,
    SPECIFIC_JOB;

    public static AnalysisMode fromString(String value) {
        if (value == null || value.isBlank()) {
            return GENERAL;
        }
        String normalized = value.trim().toUpperCase();
        for (AnalysisMode mode : values()) {
            if (mode.name().equals(normalized)) {
                return mode;
            }
        }
        throw new ResumeValidationException(ResumeValidationException.Reason.INVALID_MODE);
    }
}
