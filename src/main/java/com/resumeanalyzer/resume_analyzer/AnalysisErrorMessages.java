package com.resumeanalyzer.resume_analyzer;

import java.util.concurrent.CancellationException;
import java.util.concurrent.RejectedExecutionException;

/** Central safe-message mapping shared by SSE and ordinary MVC error handling. */
public final class AnalysisErrorMessages {

    public enum Category {
        INVALID_UPLOAD,
        FILE_TOO_LARGE,
        INVALID_PDF,
        INVALID_MODE,
        INVALID_JOB_DESCRIPTION,
        PROCESSING,
        AI_COMMUNICATION,
        AI_RESPONSE,
        AI_VALIDATION,
        BUSY,
        RATE_LIMITED,
        CANCELLED,
        TIMEOUT,
        INTERNAL
    }

    private AnalysisErrorMessages() {
    }

    public static Category classify(Throwable error) {
        if (error instanceof ResumeValidationException validation) {
            return switch (validation.getReason()) {
                case FILE_TOO_LARGE -> Category.FILE_TOO_LARGE;
                case INVALID_PDF, ENCRYPTED_PDF, TOO_MANY_PAGES, PAGE_DIMENSIONS_TOO_LARGE,
                        NO_READABLE_TEXT, TEXT_TOO_LARGE -> Category.INVALID_PDF;
                case INVALID_UPLOAD -> Category.INVALID_UPLOAD;
                case INVALID_MODE -> Category.INVALID_MODE;
                case MISSING_JOB_DESCRIPTION, JOB_DESCRIPTION_TOO_LONG, INSUFFICIENT_JOB_DESCRIPTION -> Category.INVALID_JOB_DESCRIPTION;
            };
        }
        if (error instanceof AICommunicationException) return Category.AI_COMMUNICATION;
        if (error instanceof AnalysisContractValidationException) return Category.AI_VALIDATION;
        if (error instanceof GeminiResponseException) return Category.AI_RESPONSE;
        if (error instanceof RejectedExecutionException) return Category.BUSY;
        if (error instanceof CancellationException) return Category.CANCELLED;
        if (error instanceof ResumeProcessingException) return Category.PROCESSING;
        return Category.INTERNAL;
    }

    public static String forCategory(Category category) {
        return switch (category) {
            case INVALID_UPLOAD, INVALID_PDF -> "Please upload a valid, readable PDF resume.";
            case FILE_TOO_LARGE -> "Resume file is too large. Please upload a smaller PDF.";
            case INVALID_MODE -> "Invalid analysis mode. Please select a valid analysis mode.";
            case INVALID_JOB_DESCRIPTION -> "Please enter a meaningful job description with enough detail to analyze the match.";
            case PROCESSING -> "We couldn't process this resume. Please try again.";
            case AI_COMMUNICATION -> "We couldn't complete the AI analysis. Please try again.";
            case AI_RESPONSE -> "We couldn't validate the AI analysis. Please try again.";
            case AI_VALIDATION -> "We couldn't validate the AI analysis. Please try again.";
            case BUSY -> "RefineCV is currently processing too many resumes. Please try again shortly.";
            case RATE_LIMITED -> "You're making requests too quickly. Please wait a little and try again.";
            case CANCELLED -> "The analysis was cancelled. Please try again.";
            case TIMEOUT -> "The analysis took too long to complete. Please try again.";
            case INTERNAL -> "Something went wrong while analyzing the resume. Please try again.";
        };
    }

    public static String forException(Throwable error) {
        return forCategory(classify(error));
    }

    public static String forImprovementCategory(Category category) {
        if (category == null) {
            return "Something went wrong while generating resume improvements. Please try again.";
        }
        return switch (category) {
            case AI_COMMUNICATION -> "Failed to generate resume improvements. Please try again.";
            case AI_RESPONSE, AI_VALIDATION -> "We couldn't validate the generated resume improvements. Please try again.";
            case BUSY -> "RefineCV is currently processing too many requests. Please try again shortly.";
            case RATE_LIMITED -> "You're making requests too quickly. Please wait a little and try again.";
            case TIMEOUT -> "Generating resume improvements took too long. Please try again.";
            case CANCELLED -> "The request was cancelled. Please try again.";
            default -> "Something went wrong while generating resume improvements. Please try again.";
        };
    }

    public static String forImprovementException(Throwable error) {
        return forImprovementCategory(classify(error));
    }
}
