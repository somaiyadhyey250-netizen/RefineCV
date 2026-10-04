package com.resumeanalyzer.resume_analyzer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Domain DTO representing a structured, objective side-by-side comparison
 * of two resumes against the 6 core evaluation categories.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResumeComparisonDTO(
        String comparisonId,
        String fileNameA,
        String fileNameB,
        int totalScoreA,
        int totalScoreB,
        int scoreDifference,
        String winner,
        String verdictTitle,
        String verdictExplanation,
        List<String> keyDifferentiators,
        List<CategoryComparisonDTO> categories,
        String overallTakeaway,
        List<String> resumeABorrowsFromB,
        List<String> resumeBBorrowsFromA,
        boolean identicalResumes,
        boolean unreadableResume,
        String unreadableDetails,
        String jobDescription,
        String jobContext,
        String analysisIdA,
        String analysisIdB,
        String provider,
        String createdAt
) {
    public ResumeComparisonDTO {
        if (comparisonId == null || comparisonId.isBlank()) {
            comparisonId = java.util.UUID.randomUUID().toString();
        }
        if (fileNameA == null || fileNameA.isBlank()) {
            fileNameA = "Resume A.pdf";
        }
        if (fileNameB == null || fileNameB.isBlank()) {
            fileNameB = "Resume B.pdf";
        }
        if (keyDifferentiators == null) {
            keyDifferentiators = List.of();
        } else {
            keyDifferentiators = List.copyOf(keyDifferentiators);
        }
        if (categories == null) {
            categories = List.of();
        } else {
            categories = List.copyOf(categories);
        }
        if (resumeABorrowsFromB == null) {
            resumeABorrowsFromB = List.of();
        } else {
            resumeABorrowsFromB = List.copyOf(resumeABorrowsFromB);
        }
        if (resumeBBorrowsFromA == null) {
            resumeBBorrowsFromA = List.of();
        } else {
            resumeBBorrowsFromA = List.copyOf(resumeBBorrowsFromA);
        }
        if (createdAt == null || createdAt.isBlank()) {
            createdAt = Instant.now().toString();
        }
    }

    /** Factory for identical resume detection */
    public static ResumeComparisonDTO forIdentical(String comparisonId, String fileNameA, String fileNameB, String jobDescription) {
        return new ResumeComparisonDTO(
                comparisonId,
                fileNameA,
                fileNameB,
                0,
                0,
                0,
                "TIE",
                "Identical Resumes",
                "These resumes appear to be identical, so there is no meaningful difference to compare.",
                List.of(),
                List.of(),
                "The two documents contain identical or near-identical text content. Please upload two distinct resumes to view a comparative analysis.",
                List.of(),
                List.of(),
                true,
                false,
                null,
                jobDescription,
                null,
                null,
                null,
                "system",
                Instant.now().toString()
        );
    }

    /** Factory for unreadable resume state */
    public static ResumeComparisonDTO forUnreadable(String comparisonId, String fileNameA, String fileNameB, String details, String jobDescription) {
        return new ResumeComparisonDTO(
                comparisonId,
                fileNameA,
                fileNameB,
                0,
                0,
                0,
                "TIE",
                "Comparison Unavailable",
                details != null ? details : "One or both resumes could not be reliably extracted, so a fair comparison cannot be completed.",
                List.of(),
                List.of(),
                "A fair comparison requires readable text content from both documents. Please ensure both PDFs contain extractable text.",
                List.of(),
                List.of(),
                false,
                true,
                details,
                jobDescription,
                null,
                null,
                null,
                "system",
                Instant.now().toString()
        );
    }

    public boolean isIdentical() { return identicalResumes(); }
    public boolean isUnreadable() { return unreadableResume(); }
    public String unreadableReason() { return unreadableDetails(); }
    public String winnerVerdict() { return verdictTitle(); }
    public List<String> winnerDifferentiators() { return keyDifferentiators(); }
    public List<String> borrowFromBForA() { return resumeABorrowsFromB(); }
    public List<String> borrowFromAForB() { return resumeBBorrowsFromA(); }

    /** Factory for structured comparison instantiation */
    public static ResumeComparisonDTO of(
            String comparisonId,
            String fileNameA,
            String fileNameB,
            String jobDescription,
            List<CategoryComparisonDTO> categories,
            List<String> keyDifferentiators,
            String overallTakeaway,
            List<String> resumeABorrowsFromB,
            List<String> resumeBBorrowsFromA
    ) {
        int totalA = categories.stream().mapToInt(CategoryComparisonDTO::scoreA).sum();
        int totalB = categories.stream().mapToInt(CategoryComparisonDTO::scoreB).sum();
        int diff = Math.abs(totalA - totalB);
        String winner;
        String verdict;
        String explanation;
        if (diff >= 8) {
            winner = totalA > totalB ? "A" : "B";
            verdict = (totalA > totalB ? "Resume A" : "Resume B") + " is stronger overall";
            explanation = (totalA > totalB ? "Resume A" : "Resume B") + " demonstrates a substantial overall advantage across multiple core categories.";
        } else if (diff >= 4) {
            winner = totalA > totalB ? "A" : "B";
            verdict = (totalA > totalB ? "Resume A" : "Resume B") + " has a narrow overall advantage";
            explanation = (totalA > totalB ? "Resume A" : "Resume B") + " maintains a slight edge.";
        } else {
            winner = "TIE";
            verdict = "Too close to call";
            explanation = "Both resumes are closely matched across the evaluated categories with no decisive advantage.";
        }
        List<String> diffs = "TIE".equals(winner) ? List.of() : (keyDifferentiators != null ? keyDifferentiators : List.of());
        return new ResumeComparisonDTO(
                comparisonId, fileNameA, fileNameB,
                totalA, totalB, diff, winner, verdict, explanation,
                diffs, categories, overallTakeaway,
                resumeABorrowsFromB, resumeBBorrowsFromA,
                false, false, null, jobDescription, null, null, null, "system", Instant.now().toString()
        );
    }
}
