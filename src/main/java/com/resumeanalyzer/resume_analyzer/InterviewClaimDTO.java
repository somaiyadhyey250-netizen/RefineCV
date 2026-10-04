package com.resumeanalyzer.resume_analyzer;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Domain DTO representing a high-priority claim from the resume that deserves preparation.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InterviewClaimDTO(
        String id,
        String claim,
        String riskLevel,
        String preparationNote
) {
    public InterviewClaimDTO {
        if (id == null || id.isBlank()) {
            id = java.util.UUID.randomUUID().toString();
        }
        if (claim == null || claim.isBlank()) {
            claim = "Stated experience";
        }
        riskLevel = InterviewQuestionDTO.normalizeRiskLevel(riskLevel);
        if (preparationNote == null || preparationNote.isBlank()) {
            preparationNote = "Be ready to explain your exact responsibilities and architecture.";
        }
    }

    public InterviewClaimDTO(String claim, String riskLevel, String preparationNote) {
        this(java.util.UUID.randomUUID().toString(), claim, riskLevel, preparationNote);
    }
}
