package com.resumeanalyzer.resume_analyzer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Single bullet point improvement comparing original and refined versions. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BulletImprovementDTO(
        String section,
        String original,
        String improved,
        String explanation
) {
}
