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
        private volatile String fileName;
        private volatile String error;
        private volatile ResumeImprovementDTO improvement;
        private final Instant createdAt;
        private volatile Instant updatedAt;

        public FullSession(String analysisId, AnalysisStatus status, String stage,
                           String resumeText, ResumeAnalysisDTO analysis, String provider, String error) {
            this(analysisId, status, stage, resumeText, analysis, provider, AnalysisMode.GENERAL, null, "Resume.pdf", error);
        }

        public FullSession(String analysisId, AnalysisStatus status, String stage,
                           String resumeText, ResumeAnalysisDTO analysis, String provider,
                           AnalysisMode mode, String jobDescription, String error) {
            this(analysisId, status, stage, resumeText, analysis, provider, mode, jobDescription, "Resume.pdf", error);
        }

        public FullSession(String analysisId, AnalysisStatus status, String stage,
                           String resumeText, ResumeAnalysisDTO analysis, String provider,
                           AnalysisMode mode, String jobDescription, String fileName, String error) {
            this.analysisId = analysisId;
            this.status = status;
            this.stage = stage;
            this.resumeText = resumeText;
            this.analysis = analysis;
            this.provider = provider;
            this.mode = mode != null ? mode : AnalysisMode.GENERAL;
            this.jobDescription = jobDescription;
            this.fileName = fileName != null && !fileName.isBlank() ? fileName : "Resume.pdf";
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
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getError() { return error; }
        public ResumeImprovementDTO getImprovement() { return improvement; }
        public void setImprovement(ResumeImprovementDTO improvement) {
            this.improvement = improvement;
            this.updatedAt = Instant.now();
        }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getUpdatedAt() { return updatedAt; }

        public void setStage(String stage) {
            this.stage = stage;
            this.updatedAt = Instant.now();
        }

        public void complete(String resumeText, ResumeAnalysisDTO analysis, String provider, AnalysisMode mode, String jobDescription, String fileName) {
            this.resumeText = resumeText;
            this.analysis = analysis;
            this.provider = provider;
            this.mode = mode != null ? mode : AnalysisMode.GENERAL;
            this.jobDescription = jobDescription;
            if (fileName != null && !fileName.isBlank()) {
                this.fileName = fileName;
            }
            this.status = AnalysisStatus.COMPLETED;
            this.stage = "completed";
            this.updatedAt = Instant.now();
        }

        public void complete(String resumeText, ResumeAnalysisDTO analysis, String provider, AnalysisMode mode, String jobDescription) {
            complete(resumeText, analysis, provider, mode, jobDescription, this.fileName);
        }

        public void complete(String resumeText, ResumeAnalysisDTO analysis, String provider) {
            complete(resumeText, analysis, provider, AnalysisMode.GENERAL, null, this.fileName);
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
    private final Map<String, ResumeComparisonDTO> comparisons = new ConcurrentHashMap<>();
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

    public void completeSession(String analysisId, String resumeText, ResumeAnalysisDTO analysis, String provider, AnalysisMode mode, String jobDescription, String fileName) {
        if (analysisId == null || analysisId.isBlank()) return;
        cleanExpired();
        FullSession session = sessions.computeIfAbsent(analysisId,
                id -> new FullSession(id, AnalysisStatus.COMPLETED, "completed", resumeText, analysis, provider, mode, jobDescription, fileName, null));
        session.complete(resumeText, analysis, provider, mode, jobDescription, fileName);
    }

    public void completeSession(String analysisId, String resumeText, ResumeAnalysisDTO analysis, String provider, AnalysisMode mode, String jobDescription) {
        completeSession(analysisId, resumeText, analysis, provider, mode, jobDescription, "Resume.pdf");
    }

    public void completeSession(String analysisId, String resumeText, ResumeAnalysisDTO analysis, String provider) {
        completeSession(analysisId, resumeText, analysis, provider, AnalysisMode.GENERAL, null, "Resume.pdf");
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

    public Optional<FullSession> getFullSession(String analysisId) {
        if (analysisId == null || analysisId.isBlank()) return Optional.empty();
        FullSession session = sessions.get(analysisId);
        if (session == null) return Optional.empty();
        if (Duration.between(session.getCreatedAt(), Instant.now()).toMillis() > ttlMillis) {
            sessions.remove(analysisId);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public Optional<ResumeImprovementDTO> getImprovement(String analysisId) {
        if (analysisId == null || analysisId.isBlank()) return Optional.empty();
        FullSession session = sessions.get(analysisId);
        if (session == null || session.getImprovement() == null) return Optional.empty();
        if (Duration.between(session.getCreatedAt(), Instant.now()).toMillis() > ttlMillis) {
            sessions.remove(analysisId);
            return Optional.empty();
        }
        return Optional.of(session.getImprovement());
    }

    public void saveImprovement(String analysisId, ResumeImprovementDTO improvement) {
        if (analysisId == null || analysisId.isBlank() || improvement == null) return;
        FullSession session = sessions.get(analysisId);
        if (session != null) {
            session.setImprovement(improvement);
        }
    }

    public void saveComparison(ResumeComparisonDTO comparison) {
        if (comparison == null || comparison.comparisonId() == null || comparison.comparisonId().isBlank()) return;
        ensureCapacity();
        comparisons.put(comparison.comparisonId(), comparison);
    }

    public Optional<ResumeComparisonDTO> getComparison(String comparisonId) {
        if (comparisonId == null || comparisonId.isBlank()) return Optional.empty();
        ResumeComparisonDTO comp = comparisons.get(comparisonId);
        if (comp == null) return Optional.empty();
        Instant createdAt;
        try {
            createdAt = Instant.parse(comp.createdAt());
        } catch (Exception e) {
            createdAt = Instant.now();
        }
        if (Duration.between(createdAt, Instant.now()).toMillis() > ttlMillis) {
            comparisons.remove(comparisonId);
            return Optional.empty();
        }
        return Optional.of(comp);
    }

    public java.util.List<HistoryItemDTO> getRecentCompletedSessions() {
        cleanExpired();
        java.util.List<HistoryItemDTO> list = new java.util.ArrayList<>();

        for (FullSession s : sessions.values()) {
            if (s.getStatus() == AnalysisStatus.COMPLETED && s.getAnalysis() != null) {
                if (s.getAnalysisId() == null || s.getAnalysisId().startsWith("cmp-") || "demo".equalsIgnoreCase(s.getAnalysisId())) {
                    continue;
                }
                list.add(new HistoryItemDTO(
                        s.getAnalysisId(),
                        s.getMode() != null ? s.getMode().name() : "GENERAL",
                        s.getFileName() != null && !s.getFileName().isBlank() ? s.getFileName() : "Resume.pdf",
                        s.getAnalysis() != null ? s.getAnalysis().score() : 0,
                        s.getCreatedAt().toString(),
                        "ANALYSIS",
                        null,
                        null,
                        null,
                        null
                ));
            }
        }

        for (ResumeComparisonDTO comp : comparisons.values()) {
            if (comp.comparisonId() == null || "demo".equalsIgnoreCase(comp.comparisonId())) {
                continue;
            }
            String modeStr = comp.jobDescription() != null && !comp.jobDescription().isBlank()
                    ? "SPECIFIC_JOB" : "GENERAL";
            list.add(new HistoryItemDTO(
                    comp.comparisonId(),
                    modeStr,
                    comp.fileNameA(),
                    comp.totalScoreA(),
                    comp.createdAt(),
                    "COMPARE",
                    comp.fileNameB(),
                    comp.totalScoreB(),
                    comp.verdictTitle(),
                    comp.jobContext() != null && !comp.jobContext().isBlank() ? comp.jobContext() : comp.jobDescription()
            ));
        }

        list.sort((a, b) -> b.createdAt().compareTo(a.createdAt()));
        if (list.size() > 20) {
            return list.subList(0, 20);
        }
        return list;
    }

    public void clear() {
        sessions.clear();
        comparisons.clear();
    }

    /**
     * Clears user-visible history records (completed sessions and comparisons).
     * Active processing sessions are preserved.
     */
    public void clearHistory() {
        sessions.entrySet().removeIf(entry -> {
            FullSession s = entry.getValue();
            return s != null && s.getStatus() == AnalysisStatus.COMPLETED && !"demo".equalsIgnoreCase(s.getAnalysisId());
        });
        comparisons.entrySet().removeIf(entry -> {
            ResumeComparisonDTO comp = entry.getValue();
            return comp == null || !"demo".equalsIgnoreCase(comp.comparisonId());
        });
    }

    private void ensureCapacity() {
        if (sessions.size() >= maxSessions) {
            Iterator<String> it = sessions.keySet().iterator();
            if (it.hasNext()) {
                sessions.remove(it.next());
            }
        }
        if (comparisons.size() >= maxSessions) {
            Iterator<String> it = comparisons.keySet().iterator();
            if (it.hasNext()) {
                comparisons.remove(it.next());
            }
        }
    }

    private void cleanExpired() {
        Instant now = Instant.now();
        sessions.entrySet().removeIf(entry ->
                Duration.between(entry.getValue().getCreatedAt(), now).toMillis() > ttlMillis);
        comparisons.entrySet().removeIf(entry -> {
            try {
                Instant created = Instant.parse(entry.getValue().createdAt());
                return Duration.between(created, now).toMillis() > ttlMillis;
            } catch (Exception e) {
                return false;
            }
        });
    }
}
