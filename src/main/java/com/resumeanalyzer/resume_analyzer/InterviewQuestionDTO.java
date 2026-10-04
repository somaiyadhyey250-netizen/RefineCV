package com.resumeanalyzer.resume_analyzer;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Domain DTO representing a grounded interview question derived strictly from the candidate's CV.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InterviewQuestionDTO(
        String id,
        String question,
        String questionType,
        String riskLevel,
        String basedOn,
        String interviewerIntent,
        String preparationHint
) {
    public InterviewQuestionDTO {
        if (id == null || id.isBlank()) {
            id = java.util.UUID.randomUUID().toString();
        }
        if (question == null || question.isBlank()) {
            question = "Could you elaborate on your experience in this area?";
        }
        questionType = normalizeQuestionType(questionType);
        riskLevel = normalizeRiskLevel(riskLevel);
        if (basedOn == null || basedOn.isBlank()) {
            basedOn = "Resume Experience";
        }
        if (interviewerIntent == null || interviewerIntent.isBlank()) {
            interviewerIntent = "Interviewers may ask this to understand your hands-on contribution and depth.";
        }
        if (preparationHint == null || preparationHint.isBlank()) {
            preparationHint = "Be ready to explain your exact responsibilities, decisions, and outcomes.";
        }
    }

    public InterviewQuestionDTO(
            String question,
            String questionType,
            String riskLevel,
            String basedOn,
            String interviewerIntent,
            String preparationHint
    ) {
        this(java.util.UUID.randomUUID().toString(), question, questionType, riskLevel, basedOn, interviewerIntent, preparationHint);
    }

    public static String normalizeQuestionType(String type) {
        if (type == null || type.isBlank()) return "DEPTH";
        String upper = type.trim().toUpperCase();
        return switch (upper) {
            case "PROOF" -> "PROOF";
            case "WHY" -> "WHY";
            default -> "DEPTH";
        };
    }

    public static String normalizeRiskLevel(String risk) {
        if (risk == null || risk.isBlank()) return "BE_READY";
        String upper = risk.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        return switch (upper) {
            case "SAFE" -> "SAFE";
            case "RISKY" -> "RISKY";
            default -> "BE_READY";
        };
    }
}
