package com.resumeanalyzer.resume_analyzer;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Safe summary representation of an analysis session for history display. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HistoryItemDTO(
        String analysisId,
        String mode,
        String fileName,
        int score,
        String createdAt
) {
}
