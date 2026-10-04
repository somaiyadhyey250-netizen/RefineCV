package com.resumeanalyzer.resume_analyzer;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Domain DTO representing constructive feedback on a candidate's practiced answer.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InterviewAnswerEvaluationDTO(
        String questionId,
        String answerQuality,
        List<String> strengths,
        List<String> improvements
) {
    public InterviewAnswerEvaluationDTO {
        if (questionId == null || questionId.isBlank()) {
            questionId = "question";
        }
        if (answerQuality == null || answerQuality.isBlank()) {
            answerQuality = "Your answer provides useful context. Consider expanding on concrete outcomes and personal contributions.";
        }
        if (strengths == null) {
            strengths = List.of();
        } else {
            strengths = List.copyOf(strengths);
        }
        if (improvements == null) {
            improvements = List.of();
        } else {
            improvements = List.copyOf(improvements);
        }
    }
}
