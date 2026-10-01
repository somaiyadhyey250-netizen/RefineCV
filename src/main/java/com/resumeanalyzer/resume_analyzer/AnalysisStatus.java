package com.resumeanalyzer.resume_analyzer;

/** Lifecycle statuses for asynchronous resume analysis jobs. */
public enum AnalysisStatus {
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED
}
