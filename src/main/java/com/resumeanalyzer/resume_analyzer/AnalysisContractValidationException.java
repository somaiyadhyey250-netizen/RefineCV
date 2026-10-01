package com.resumeanalyzer.resume_analyzer;

/** A parsed model response failed RefineCV's application-level business rules. */
public class AnalysisContractValidationException extends RuntimeException {
    public enum Reason { SCORE_OUT_OF_RANGE, BLANK_REQUIRED_TEXT, BLANK_LIST_ITEM }

    private final Reason reason;

    public AnalysisContractValidationException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
