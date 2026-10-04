package com.resumeanalyzer.resume_analyzer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Domain DTO representing a complete, standalone Interview Prep from CV result.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InterviewPrepDTO(
        String id,
        String filename,
        String createdAt,
        int questionCount,
        List<InterviewQuestionDTO> questions,
        List<InterviewClaimDTO> claimsToPrepare,
        String overallPreparationNote,
        boolean unreadableResume,
        String unreadableDetails,
        String provider
) {
    public InterviewPrepDTO(
            String id,
            String filename,
            String createdAt,
            int questionCount,
            List<InterviewQuestionDTO> questions,
            List<InterviewClaimDTO> claimsToPrepare,
            String overallPreparationNote,
            boolean unreadableResume
    ) {
        this(id, filename, createdAt, questionCount, questions, claimsToPrepare, overallPreparationNote, unreadableResume, null, null);
    }
    public InterviewPrepDTO {
        if (id == null || id.isBlank()) {
            id = java.util.UUID.randomUUID().toString();
        }
        if (filename == null || filename.isBlank()) {
            filename = "Resume.pdf";
        }
        if (createdAt == null || createdAt.isBlank()) {
            createdAt = Instant.now().toString();
        }
        if (questions == null) {
            questions = List.of();
        } else {
            questions = List.copyOf(questions);
        }
        if (claimsToPrepare == null) {
            claimsToPrepare = List.of();
        } else {
            claimsToPrepare = List.copyOf(claimsToPrepare);
        }
        questionCount = questions.size();
        if (!unreadableResume && questions.isEmpty()) {
            throw new IllegalArgumentException("Interview prep must have at least one question when readable.");
        }
        if (overallPreparationNote == null || overallPreparationNote.isBlank()) {
            overallPreparationNote = "Your resume contains project and technology claims that are likely to invite follow-up questions. Focus your preparation on explaining your exact contribution, technical decisions, and measurable outcomes.";
        }
    }

    public static InterviewPrepDTO forUnreadable(String id, String filename, String details) {
        return new InterviewPrepDTO(
                id,
                filename,
                Instant.now().toString(),
                0,
                List.of(),
                List.of(),
                details != null ? details : "The resume could not be reliably extracted, so personalized interview questions cannot be generated.",
                true,
                details,
                "system"
        );
    }

    public InterviewPrepDTO withMergedQuestions(List<InterviewQuestionDTO> additionalQuestions) {
        if (additionalQuestions == null || additionalQuestions.isEmpty()) {
            return this;
        }
        List<InterviewQuestionDTO> merged = new ArrayList<>(this.questions);
        for (InterviewQuestionDTO q : additionalQuestions) {
            if (q == null) continue;
            boolean duplicate = merged.stream().anyMatch(existing ->
                    existing.question().equalsIgnoreCase(q.question()) || existing.id().equals(q.id()));
            if (!duplicate) {
                merged.add(q);
                if (merged.size() >= 15) {
                    break;
                }
            }
        }
        return new InterviewPrepDTO(
                this.id,
                this.filename,
                this.createdAt,
                merged.size(),
                merged,
                this.claimsToPrepare,
                this.overallPreparationNote,
                this.unreadableResume,
                this.unreadableDetails,
                this.provider
        );
    }

    public boolean isUnreadable() {
        return unreadableResume;
    }
}
