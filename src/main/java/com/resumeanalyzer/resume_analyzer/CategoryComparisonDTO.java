package com.resumeanalyzer.resume_analyzer;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents the evaluation of a single scoring category comparing Candidate A and Candidate B.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CategoryComparisonDTO(
        String categoryId,
        String name,
        int maxPoints,
        int scoreA,
        int scoreB,
        String evidenceA,
        String evidenceB,
        String explanationA,
        String explanationB,
        String winner
) {
    public CategoryComparisonDTO {
        if (categoryId == null || categoryId.isBlank()) {
            categoryId = "unknown";
        }
        if (name == null || name.isBlank()) {
            name = "Category";
        }
        if (maxPoints <= 0) {
            maxPoints = 20;
        }
        scoreA = Math.max(0, Math.min(scoreA, maxPoints));
        scoreB = Math.max(0, Math.min(scoreB, maxPoints));
        if (winner == null || winner.isBlank()) {
            if (scoreA > scoreB) winner = "A";
            else if (scoreB > scoreA) winner = "B";
            else winner = "TIE";
        }
    }

    public CategoryComparisonDTO(String name, int maxPoints, int scoreA, int scoreB, String winner, String evidenceA, String evidenceB, String explanationA, String explanationB) {
        this(name != null ? name.toLowerCase().replaceAll("[^a-z0-9]+", "_") : "unknown",
                name, maxPoints, scoreA, scoreB, evidenceA, evidenceB, explanationA, explanationB, winner);
    }
}
