package com.resumeanalyzer.resume_analyzer;

/** Safe, categorized validation failure for an uploaded resume. */
public class ResumeValidationException extends RuntimeException {

    public enum Reason {
        INVALID_UPLOAD,
        FILE_TOO_LARGE,
        INVALID_PDF,
        ENCRYPTED_PDF,
        TOO_MANY_PAGES,
        PAGE_DIMENSIONS_TOO_LARGE,
        NO_READABLE_TEXT,
        TEXT_TOO_LARGE,
        INVALID_MODE,
        MISSING_JOB_DESCRIPTION,
        JOB_DESCRIPTION_TOO_LONG,
        INSUFFICIENT_JOB_DESCRIPTION
    }

    private final Reason reason;

    public ResumeValidationException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public ResumeValidationException(Reason reason, Throwable cause) {
        super(reason.name(), cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
