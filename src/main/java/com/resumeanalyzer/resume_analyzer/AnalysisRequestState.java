package com.resumeanalyzer.resume_analyzer;

import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;

/** Per-request lifecycle state; never shared between analysis requests. */
final class AnalysisRequestState {

    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicBoolean terminal = new AtomicBoolean();
    private final AtomicReference<Future<?>> task = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> timeout = new AtomicReference<>();
    private final String analysisId;
    private final long startedAtNanos = System.nanoTime();
    private volatile String stage = "upload";

    AnalysisRequestState() {
        this(UUID.randomUUID().toString());
    }

    AnalysisRequestState(String analysisId) {
        this.analysisId = analysisId != null && !analysisId.isBlank()
                ? analysisId
                : UUID.randomUUID().toString();
    }

    String getAnalysisId() {
        return analysisId;
    }

    String getStage() {
        return stage;
    }

    void setStage(String stage) {
        this.stage = stage;
    }

    long elapsedMillis() {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }

    boolean isActive() {
        return !cancelled.get() && !terminal.get();
    }

    boolean isCancelled() {
        return cancelled.get();
    }

    boolean isTerminal() {
        return terminal.get();
    }

    boolean tryBeginTerminal() {
        boolean started = terminal.compareAndSet(false, true);
        if (started) {
            cancelTimeout();
        }
        return started;
    }

    void checkActive() {
        if (!isActive() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Analysis request is no longer active.");
        }
    }

    void setTask(Future<?> submittedTask) {
        task.set(submittedTask);
        if (cancelled.get()) {
            submittedTask.cancel(true);
        }
    }

    void setTimeout(ScheduledFuture<?> scheduledTimeout) {
        timeout.set(scheduledTimeout);
        if (terminal.get() || cancelled.get()) {
            scheduledTimeout.cancel(false);
        }
    }

    void cancel() {
        cancelled.set(true);
        Future<?> submittedTask = task.get();
        if (submittedTask != null) {
            submittedTask.cancel(true);
        }
        cancelTimeout();
    }

    void cancelTimeout() {
        ScheduledFuture<?> scheduledTimeout = timeout.getAndSet(null);
        if (scheduledTimeout != null) {
            scheduledTimeout.cancel(false);
        }
    }
}
