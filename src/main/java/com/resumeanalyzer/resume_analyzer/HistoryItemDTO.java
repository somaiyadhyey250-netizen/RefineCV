package com.resumeanalyzer.resume_analyzer;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Safe summary representation of an analysis session or comparison for history display. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HistoryItemDTO(
        String analysisId,
        String mode,
        String fileName,
        int score,
        String createdAt,
        String type,
        String fileNameB,
        Integer scoreB,
        String verdict,
        String jobContext
) {
    public HistoryItemDTO(String analysisId, String mode, String fileName, int score, String createdAt) {
        this(analysisId, mode, fileName, score, createdAt, "ANALYSIS", null, null, null, null);
    }
}
