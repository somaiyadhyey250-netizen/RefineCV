package com.resumeanalyzer.resume_analyzer;

/** A non-validation failure while extracting or OCR-processing a resume. */
public class ResumeProcessingException extends RuntimeException {
    public enum Stage { PDF_EXTRACTION, OCR }

    private final Stage stage;

    public ResumeProcessingException(Stage stage, Throwable cause) {
        super(stage.name(), cause);
        this.stage = stage;
    }

    public Stage getStage() {
        return stage;
    }
}
