package com.resumeanalyzer.resume_analyzer;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Typed response contract for V4.1 resume improvement results. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResumeImprovementDTO(
        String improvedSummary,
        List<BulletImprovementDTO> bulletImprovements,
        List<String> improvementExplanations,
        List<String> actionableChanges
) {
}
