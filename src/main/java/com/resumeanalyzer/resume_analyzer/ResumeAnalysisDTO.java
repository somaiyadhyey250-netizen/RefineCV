package com.resumeanalyzer.resume_analyzer;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Typed response contract returned by the resume analysis model. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResumeAnalysisDTO(
        Integer score,
        String summary,
        List<String> strongestSkills,
        List<String> missingOrWeakSkills,
        List<String> strengths,
        List<String> weaknesses,
        String atsCompatibility,
        List<String> suggestions,
        List<String> recommendedChanges
) {
}
