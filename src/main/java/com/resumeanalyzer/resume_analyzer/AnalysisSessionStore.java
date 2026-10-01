package com.resumeanalyzer.resume_analyzer;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Thread-safe, bounded in-memory cache of resume analyses and session recovery states.
 * Allows V4.1 resume improvement and reliable tab-recovery without resending or re-extracting PDF documents.
 */
@Component
public class AnalysisSessionStore {

    public record Session(
            String analysisId,
            String resumeText,
            ResumeAnalysisDTO analysis,
            AnalysisMode mode,
            String jobDescription,
            Instant createdAt
    ) {
        public Session(String analysisId, String resumeText, ResumeAnalysisDTO analysis, Instant createdAt) {
            this(analysisId, resumeText, analysis, AnalysisMode.GENERAL, null, createdAt);
        }
    }

    public static class FullSession {
        private final String analysisId;
        private volatile AnalysisStatus status;
        private volatile String stage;
        private volatile String resumeText;
        private volatile ResumeAnalysisDTO analysis;
        private volatile String provider;
        private volatile AnalysisMode mode;
        private volatile String jobDescription;
        private volatile String error;
        private final Instant createdAt;
        private volatile Instant updatedAt;

        public FullSession(String analysisId, AnalysisStatus status, String stage,
                           String resumeText, ResumeAnalysisDTO analysis, String provider, String error) {
            this(analysisId, status, stage, resumeText, analysis, provider, AnalysisMode.GENERAL, null, error);
        }

        public FullSession(String analysisId, AnalysisStatus status, String stage,
                           String resumeText, ResumeAnalysisDTO analysis, String provider,
                           AnalysisMode mode, String jobDescription, String error) {
            this.analysisId = analysisId;
            this.status = status;
            this.stage = stage;
            this.resumeText = resumeText;
            this.analysis = analysis;
            this.provider = provider;
            this.mode = mode != null ? mode : AnalysisMode.GENERAL;
            this.jobDescription = jobDescription;
            this.error = error;
            this.createdAt = Instant.now();
            this.updatedAt = this.createdAt;
        }

        public String getAnalysisId() { return analysisId; }
        public AnalysisStatus getStatus() { return status; }
        public String getStage() { return stage; }
        public String getResumeText() { return resumeText; }
        public ResumeAnalysisDTO getAnalysis() { return analysis; }
        public String getProvider() { return provider; }
        public AnalysisMode getMode() { return mode; }
        public String getJobDescription() { return jobDescription; }
        public String getError() { return error; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getUpdatedAt() { return updatedAt; }

        public void setStage(String stage) {
            this.stage = stage;
            this.updatedAt = Instant.now();
        }

        public void complete(String resumeText, ResumeAnalysisDTO analysis, String provider, AnalysisMode mode, String jobDescription) {
            this.resumeText = resumeText;
            this.analysis = analysis;
            this.provider = provider;
            this.mode = mode != null ? mode : AnalysisMode.GENERAL;
            this.jobDescription = jobDescription;
            this.status = AnalysisStatus.COMPLETED;
            this.stage = "completed";
            this.updatedAt = Instant.now();
        }

        public void complete(String resumeText, ResumeAnalysisDTO analysis, String provider) {
            complete(resumeText, analysis, provider, AnalysisMode.GENERAL, null);
        }

        public void fail(String error) {
            this.error = error;
            this.status = AnalysisStatus.FAILED;
            this.stage = "failed";
            this.updatedAt = Instant.now();
        }

        public void cancel() {
            this.status = AnalysisStatus.CANCELLED;
            this.stage = "cancelled";
            this.updatedAt = Instant.now();
        }

        public AnalysisStatusDTO toStatusDTO() {
            return new AnalysisStatusDTO(analysisId, status, stage, analysis, provider, error);
        }

        public Session toLegacySession() {
            return new Session(analysisId, resumeText, analysis, mode, jobDescription, createdAt);
        }
    }

    private final Map<String, FullSession> sessions = new ConcurrentHashMap<>();
    private final long ttlMillis;
    private final int maxSessions;

    public AnalysisSessionStore(
            @Value("${refinecv.analysis.session-ttl:30m}") Duration ttl,
            @Value("${refinecv.analysis.max-cached-sessions:1000}") int maxSessions
    ) {
        if (ttl.isZero() || ttl.isNegative() || maxSessions < 1) {
            throw new IllegalArgumentException("Session store parameters must be positive.");
        }
        this.ttlMillis = ttl.toMillis();
        this.maxSessions = maxSessions;
    }

    public void startSession(String analysisId) {
        if (analysisId == null || analysisId.isBlank()) return;
        cleanExpired();
        ensureCapacity();
        sessions.put(analysisId, new FullSession(analysisId, AnalysisStatus.PROCESSING, "upload", null, null, null, null));
    }

    public void updateProgress(String analysisId, String stage) {
        if (analysisId == null || analysisId.isBlank()) return;
        FullSession session = sessions.get(analysisId);
        if (session != null && session.getStatus() == AnalysisStatus.PROCESSING) {
            session.setStage(stage);
        }
    }

    public void completeSession(String analysisId, String resumeText, ResumeAnalysisDTO analysis, String provider, AnalysisMode mode, String jobDescription) {
        if (analysisId == null || analysisId.isBlank()) return;
        cleanExpired();
        FullSession session = sessions.computeIfAbsent(analysisId,
                id -> new FullSession(id, AnalysisStatus.COMPLETED, "completed", resumeText, analysis, provider, mode, jobDescription, null));
        session.complete(resumeText, analysis, provider, mode, jobDescription);
    }

    public void completeSession(String analysisId, String resumeText, ResumeAnalysisDTO analysis, String provider) {
        completeSession(analysisId, resumeText, analysis, provider, AnalysisMode.GENERAL, null);
    }

    public void save(String analysisId, String resumeText, ResumeAnalysisDTO analysis, String provider, AnalysisMode mode, String jobDescription) {
        completeSession(analysisId, resumeText, analysis, provider, mode, jobDescription);
    }

    public void save(String analysisId, String resumeText, ResumeAnalysisDTO analysis, String provider) {
        completeSession(analysisId, resumeText, analysis, provider, AnalysisMode.GENERAL, null);
    }

    public void failSession(String analysisId, String safeError) {
        if (analysisId == null || analysisId.isBlank()) return;
        FullSession session = sessions.computeIfAbsent(analysisId,
                id -> new FullSession(id, AnalysisStatus.FAILED, "failed", null, null, null, safeError));
        session.fail(safeError);
    }

    public void cancelSession(String analysisId) {
        if (analysisId == null || analysisId.isBlank()) return;
        FullSession session = sessions.computeIfAbsent(analysisId,
                id -> new FullSession(id, AnalysisStatus.CANCELLED, "cancelled", null, null, null, null));
        session.cancel();
    }

    public Optional<AnalysisStatusDTO> getStatus(String analysisId) {
        if (analysisId == null || analysisId.isBlank()) return Optional.empty();
        FullSession session = sessions.get(analysisId);
        if (session == null) return Optional.empty();
        if (Duration.between(session.getCreatedAt(), Instant.now()).toMillis() > ttlMillis) {
            sessions.remove(analysisId);
            return Optional.empty();
        }
        return Optional.of(session.toStatusDTO());
    }

    public void put(String analysisId, String resumeText, ResumeAnalysisDTO analysis) {
        completeSession(analysisId, resumeText, analysis, "gemini");
    }

    public Optional<Session> get(String analysisId) {
        if (analysisId == null || analysisId.isBlank()) return Optional.empty();
        FullSession session = sessions.get(analysisId);
        if (session == null) return Optional.empty();
        if (Duration.between(session.getCreatedAt(), Instant.now()).toMillis() > ttlMillis) {
            sessions.remove(analysisId);
            return Optional.empty();
        }
        return Optional.of(session.toLegacySession());
    }

    public void remove(String analysisId) {
        if (analysisId != null) sessions.remove(analysisId);
    }

    public void clear() {
        sessions.clear();
    }

    private void ensureCapacity() {
        if (sessions.size() >= maxSessions) {
            Iterator<String> it = sessions.keySet().iterator();
            if (it.hasNext()) {
                sessions.remove(it.next());
            }
        }
    }

    private void cleanExpired() {
        Instant now = Instant.now();
        sessions.entrySet().removeIf(entry ->
                Duration.between(entry.getValue().getCreatedAt(), now).toMillis() > ttlMillis);
    }
}
