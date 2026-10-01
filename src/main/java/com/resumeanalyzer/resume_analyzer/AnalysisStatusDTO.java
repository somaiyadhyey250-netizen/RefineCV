package com.resumeanalyzer.resume_analyzer;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Safe status and recovery representation of an analysis session, containing no raw resume text or secrets. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnalysisStatusDTO(
        String analysisId,
        AnalysisStatus status,
        String stage,
        ResumeAnalysisDTO result,
        String provider,
        String error
) {
}
