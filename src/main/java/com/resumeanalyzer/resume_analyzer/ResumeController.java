package com.resumeanalyzer.resume_analyzer;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

@Controller
public class ResumeController {

    private static final Logger logger = LoggerFactory.getLogger(ResumeController.class);


    private final ResumeTextExtractor resumeTextExtractor;

    private final AIProvider aiProvider;

    private final long maxUploadBytes;
    private final ThreadPoolTaskExecutor analysisExecutor;
    private final ThreadPoolTaskScheduler timeoutScheduler;
    private final Duration analysisTimeout;
    private final Duration sseTimeoutGrace;
    private final InMemoryAnalysisRateLimiter rateLimiter;
    private final AnalysisSessionStore sessionStore;
    private final Map<String, AnalysisRequestState> activeRequests = new ConcurrentHashMap<>();


    @Autowired
    public ResumeController(
            ResumeTextExtractor resumeTextExtractor,
            AIProvider aiProvider,
            @Value("${refinecv.upload.max-file-size}") DataSize maxUploadSize,
            @Qualifier("resumeAnalysisExecutor") ThreadPoolTaskExecutor analysisExecutor,
            @Qualifier("analysisTimeoutScheduler") ThreadPoolTaskScheduler timeoutScheduler,
            @Value("${refinecv.analysis.timeout}") Duration analysisTimeout,
            @Value("${refinecv.analysis.sse-timeout-grace}") Duration sseTimeoutGrace,
            InMemoryAnalysisRateLimiter rateLimiter,
            @Autowired(required = false) AnalysisSessionStore sessionStore
    ) {

        this.resumeTextExtractor =
                resumeTextExtractor;

        this.aiProvider =
                aiProvider;

        this.maxUploadBytes = maxUploadSize.toBytes();
        this.analysisExecutor = analysisExecutor;
        this.timeoutScheduler = timeoutScheduler;
        this.analysisTimeout = analysisTimeout;
        this.sseTimeoutGrace = sseTimeoutGrace;
        this.rateLimiter = rateLimiter;
        this.sessionStore = sessionStore != null
                ? sessionStore
                : new AnalysisSessionStore(Duration.ofMinutes(30), 1000);

    }

    public ResumeController(
            ResumeTextExtractor resumeTextExtractor,
            GeminiService geminiService,
            DataSize maxUploadSize,
            ThreadPoolTaskExecutor analysisExecutor,
            ThreadPoolTaskScheduler timeoutScheduler,
            Duration analysisTimeout,
            Duration sseTimeoutGrace,
            InMemoryAnalysisRateLimiter rateLimiter
    ) {
        this(resumeTextExtractor, (AIProvider) geminiService, maxUploadSize, analysisExecutor, timeoutScheduler,
                analysisTimeout, sseTimeoutGrace, rateLimiter,
                new AnalysisSessionStore(Duration.ofMinutes(30), 1000));
    }

    public ResumeController(
            ResumeTextExtractor resumeTextExtractor,
            AIProvider aiProvider,
            DataSize maxUploadSize,
            ThreadPoolTaskExecutor analysisExecutor,
            ThreadPoolTaskScheduler timeoutScheduler,
            Duration analysisTimeout,
            Duration sseTimeoutGrace,
            InMemoryAnalysisRateLimiter rateLimiter
    ) {
        this(resumeTextExtractor, aiProvider, maxUploadSize, analysisExecutor, timeoutScheduler,
                analysisTimeout, sseTimeoutGrace, rateLimiter,
                new AnalysisSessionStore(Duration.ofMinutes(30), 1000));
    }


    /* =========================================
       HOME PAGE
    ========================================= */

    @GetMapping("/")
    public String home() {

        return "index";

    }


    /* =========================================
       RESUME ANALYSIS
    ========================================= */

    @PostMapping(
            value = "/analyze",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    @ResponseBody
    public SseEmitter analyzeResume(
            @RequestParam(value = "resume", required = false)
            MultipartFile resume,
            @RequestParam(value = "mode", required = false)
            String mode,
            @RequestParam(value = "jobDescription", required = false)
            String jobDescription,
            HttpServletRequest request,
            HttpServletResponse response
    ) {


        /*
         * SseEmitter keeps the HTTP connection
         * open while the resume is being processed.
         *
         * This allows the backend to send
         * progress updates to the browser.
         */

        SseEmitter emitter = new SseEmitter(
                analysisTimeout.plus(sseTimeoutGrace).toMillis()
        );
        AnalysisRequestState state = new AnalysisRequestState();
        if (response != null) {
            response.setHeader("X-Analysis-Id", state.getAnalysisId());
        }
        activeRequests.put(state.getAnalysisId(), state);
        sessionStore.startSession(state.getAnalysisId());
        logger.info("analysis_request_started analysisId={} uploadBytes={}",
                state.getAnalysisId(), resume == null ? 0 : resume.getSize());

        emitter.onTimeout(() -> {
            logger.debug("sse_emitter_timeout analysisId={}", state.getAnalysisId());
        });
        emitter.onError(error -> {
            logger.debug("sse_emitter_error analysisId={} reason={}", state.getAnalysisId(),
                    error != null ? error.getMessage() : "unknown");
        });
        emitter.onCompletion(() -> {
            logger.debug("sse_emitter_complete analysisId={}", state.getAnalysisId());
        });

        if (!rateLimiter.tryAcquire(request.getRemoteAddr())) {
            logger.warn("analysis_request_rejected analysisId={} category={}",
                    state.getAnalysisId(), AnalysisErrorMessages.Category.RATE_LIMITED);
            finishError(emitter, state,
                    AnalysisErrorMessages.forCategory(AnalysisErrorMessages.Category.RATE_LIMITED));
            return emitter;
        }

        try {
            validateUpload(resume);
        } catch (ResumeValidationException e) {
            logger.warn("analysis_request_rejected analysisId={} category={} errorType={}",
                    state.getAnalysisId(), AnalysisErrorMessages.classify(e), e.getClass().getName());
            finishError(emitter, state, AnalysisErrorMessages.forException(e));
            return emitter;
        }

        final AnalysisMode resolvedMode = AnalysisMode.fromString(mode);
        final String resolvedJobDesc = (resolvedMode == AnalysisMode.SPECIFIC_JOB
                && jobDescription != null && !jobDescription.isBlank())
                ? jobDescription.strip()
                : null;

        try {
            Future<?> task = analysisExecutor.submit(() -> runAnalysis(resume, emitter, state, resolvedMode, resolvedJobDesc));
            state.setTask(task);

            ScheduledFuture<?> timeout = timeoutScheduler.schedule(
                    () -> handleTimeout(emitter, state),
                    Instant.now().plus(analysisTimeout)
            );
            if (timeout != null) {
                state.setTimeout(timeout);
            }
        } catch (RejectedExecutionException e) {
            logger.warn("analysis_request_rejected analysisId={} category={} errorType={}",
                    state.getAnalysisId(), AnalysisErrorMessages.Category.BUSY, e.getClass().getName());
            finishError(emitter, state, AnalysisErrorMessages.forException(e));
        } catch (RuntimeException e) {
            logger.error("analysis_start_failed analysisId={} errorType={}",
                    state.getAnalysisId(), e.getClass().getName());
            state.cancel();
            finishError(emitter, state, AnalysisErrorMessages.forException(e));
        }

        return emitter;
    }

    public SseEmitter analyzeResume(MultipartFile resume, HttpServletRequest request) {
        return analyzeResume(resume, null, null, request, null);

    }

    void validateUpload(MultipartFile resume) {
        if (resume == null || resume.isEmpty()) {
            throw new ResumeValidationException(ResumeValidationException.Reason.INVALID_UPLOAD);
        }

        if (resume.getSize() > maxUploadBytes) {
            throw new ResumeValidationException(ResumeValidationException.Reason.FILE_TOO_LARGE);
        }
    }

    void runAnalysis(
            MultipartFile resume,
            SseEmitter emitter,
            AnalysisRequestState state
    ) {
        runAnalysis(resume, emitter, state, AnalysisMode.GENERAL, null);
    }

    void runAnalysis(
            MultipartFile resume,
            SseEmitter emitter,
            AnalysisRequestState state,
            AnalysisMode mode,
            String jobDescription
    ) {
        MDC.put("analysisId", state.getAnalysisId());
        logger.info("analysis_started analysisId={} uploadBytes={}", state.getAnalysisId(), resume.getSize());
        try {
            state.checkActive();
            sendEvent(emitter, state, "analysis-id", state.getAnalysisId());
            sendStatus(emitter, state, "upload");

            byte[] pdfBytes = resume.getBytes();
            state.checkActive();

            String resumeText = resumeTextExtractor.extractText(
                    pdfBytes,
                    status -> sendStatus(emitter, state, status)
            );

            state.checkActive();
            final AnalysisMode effectiveMode = mode != null ? mode : AnalysisMode.GENERAL;
            AIAnalysisResult aiResult = aiProvider.analyzeResumeWithProvider(
                    resumeText,
                    effectiveMode,
                    jobDescription,
                    status -> sendStatus(emitter, state, status)
            );
            ResumeAnalysisDTO analysis = aiResult.analysis();

            sessionStore.completeSession(state.getAnalysisId(), resumeText, analysis, aiResult.providerName(), effectiveMode, jobDescription);
            if (aiResult.providerName() != null && !aiResult.providerName().isBlank()) {
                sendEvent(emitter, state, "provider", aiResult.providerName());
            }
            finishResult(emitter, state, analysis);
            logger.info("analysis_succeeded analysisId={} provider={} durationMs={}",
                    state.getAnalysisId(), aiResult.providerName(), state.elapsedMillis());
        } catch (CancellationException e) {
            sessionStore.cancelSession(state.getAnalysisId());
            logger.info("analysis_cancelled analysisId={} stage={} durationMs={}",
                    state.getAnalysisId(), state.getStage(), state.elapsedMillis());
            finishError(emitter, state, AnalysisErrorMessages.forException(e));
        } catch (AnalysisContractValidationException e) {
            logger.warn("analysis_failed analysisId={} stage=ai category={} reason={} durationMs={}",
                    state.getAnalysisId(), AnalysisErrorMessages.Category.AI_VALIDATION,
                    e.getReason(), state.elapsedMillis());
            sessionStore.failSession(state.getAnalysisId(), AnalysisErrorMessages.forException(e));
            finishError(emitter, state, AnalysisErrorMessages.forException(e));
        } catch (GeminiResponseException e) {
            logger.warn("analysis_failed analysisId={} stage=ai category={} reason={} durationMs={}",
                    state.getAnalysisId(), AnalysisErrorMessages.Category.AI_RESPONSE,
                    e.getReason(), state.elapsedMillis());
            sessionStore.failSession(state.getAnalysisId(), AnalysisErrorMessages.forException(e));
            finishError(emitter, state, AnalysisErrorMessages.forException(e));
        } catch (AICommunicationException e) {
            logger.error("analysis_failed analysisId={} stage=ai category={} provider={} durationMs={}",
                    state.getAnalysisId(), AnalysisErrorMessages.Category.AI_COMMUNICATION,
                    e.getProvider(), state.elapsedMillis());
            sessionStore.failSession(state.getAnalysisId(), AnalysisErrorMessages.forException(e));
            finishError(emitter, state, AnalysisErrorMessages.forException(e));
        } catch (ResumeValidationException e) {
            logger.warn("analysis_failed analysisId={} stage={} category={} reason={} durationMs={}",
                    state.getAnalysisId(), state.getStage(), AnalysisErrorMessages.classify(e),
                    e.getReason(), state.elapsedMillis());
            sessionStore.failSession(state.getAnalysisId(), AnalysisErrorMessages.forException(e));
            finishError(emitter, state, AnalysisErrorMessages.forException(e));
        } catch (ResumeProcessingException e) {
            logger.error("analysis_failed analysisId={} stage={} category={} causeType={} durationMs={}",
                    state.getAnalysisId(), e.getStage(), AnalysisErrorMessages.Category.PROCESSING,
                    e.getCause() == null ? "none" : e.getCause().getClass().getName(), state.elapsedMillis());
            sessionStore.failSession(state.getAnalysisId(), AnalysisErrorMessages.forException(e));
            finishError(emitter, state, AnalysisErrorMessages.forException(e));
        } catch (Exception e) {
            if (state.isCancelled() || Thread.currentThread().isInterrupted()) {
                sessionStore.cancelSession(state.getAnalysisId());
                return;
            }
            logger.error("analysis_failed analysisId={} stage={} category={} errorType={} durationMs={}",
                    state.getAnalysisId(), state.getStage(), AnalysisErrorMessages.Category.INTERNAL,
                    e.getClass().getName(), state.elapsedMillis());
            sessionStore.failSession(state.getAnalysisId(), AnalysisErrorMessages.forCategory(AnalysisErrorMessages.Category.INTERNAL));
            finishError(emitter, state, AnalysisErrorMessages.forCategory(AnalysisErrorMessages.Category.INTERNAL));
        } finally {
            activeRequests.remove(state.getAnalysisId());
            state.cancelTimeout();
            MDC.remove("analysisId");
        }
    }


    /* =========================================
       SEND STATUS
    ========================================= */

    private void sendStatus(SseEmitter emitter, AnalysisRequestState state, String status) {
        state.checkActive();
        state.setStage(status);
        sessionStore.updateProgress(state.getAnalysisId(), status);
        logger.info("analysis_stage analysisId={} stage={}", state.getAnalysisId(), status);
        sendEvent(emitter, state, "status", status);
    }


    /* =========================================
       SEND SSE EVENT
    ========================================= */

    private boolean sendEvent(
            SseEmitter emitter,
            AnalysisRequestState state,
            String eventName,
            Object data
    ) {
        if (data == null) {
            return false;
        }


        try {


            emitter.send(

                    SseEmitter.event()

                            .name(eventName)

                            .data(data, data instanceof ResumeAnalysisDTO
                                    ? MediaType.APPLICATION_JSON
                                    : MediaType.TEXT_PLAIN)

            );
            return true;


        } catch (IOException e) {


            logger.debug("sse_event_send_failed analysisId={} event={} reason={}",
                    state.getAnalysisId(), eventName, e.getMessage());
            emitter.completeWithError(e);
            return false;

        }

    }

    private void finishResult(SseEmitter emitter, AnalysisRequestState state, ResumeAnalysisDTO result) {
        if (!state.tryBeginTerminal()) {
            return;
        }
        sendEvent(emitter, state, "analysis-id", state.getAnalysisId());
        if (sendEvent(emitter, state, "result", result)) {
            emitter.complete();
        }
    }

    private void finishError(SseEmitter emitter, AnalysisRequestState state, String message) {
        if (!state.tryBeginTerminal()) {
            return;
        }
        sendEvent(emitter, state, "error", message);
        emitter.complete();
    }

    private void handleTimeout(SseEmitter emitter, AnalysisRequestState state) {
        if (!state.tryBeginTerminal()) {
            return;
        }
        logger.warn("analysis_failed analysisId={} stage={} category={} durationMs={}",
                state.getAnalysisId(), state.getStage(), AnalysisErrorMessages.Category.TIMEOUT,
                state.elapsedMillis());
        sessionStore.failSession(state.getAnalysisId(),
                AnalysisErrorMessages.forCategory(AnalysisErrorMessages.Category.TIMEOUT));
        sendEvent(emitter, state, "error",
                AnalysisErrorMessages.forCategory(AnalysisErrorMessages.Category.TIMEOUT));
        state.cancel();
        activeRequests.remove(state.getAnalysisId());
        try {
            emitter.complete();
        } catch (Exception ignored) {}
    }

    /* =========================================
       RESUME IMPROVEMENT (V4.1)
    ========================================= */

    @PostMapping(
            value = "/improve",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseBody
    public CompletableFuture<ResponseEntity<?>> improveResume(
            @RequestBody(required = false) ResumeImprovementRequest request,
            HttpServletRequest httpRequest
    ) {
        if (!rateLimiter.tryAcquire(httpRequest.getRemoteAddr())) {
            logger.warn("improvement_request_rejected category={}", AnalysisErrorMessages.Category.RATE_LIMITED);
            return CompletableFuture.completedFuture(
                    ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                            .body(Map.of("error", AnalysisErrorMessages.forImprovementCategory(AnalysisErrorMessages.Category.RATE_LIMITED)))
            );
        }

        if (request == null) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("error", "Invalid improvement request."))
            );
        }

        String resumeText = null;
        ResumeAnalysisDTO analysis = null;

        if (request.analysisId() != null && !request.analysisId().isBlank()) {
            var sessionOpt = sessionStore.get(request.analysisId());
            if (sessionOpt.isPresent()) {
                resumeText = sessionOpt.get().resumeText();
                analysis = sessionOpt.get().analysis();
            }
        }
        if (resumeText == null && request.resumeText() != null && !request.resumeText().isBlank()) {
            resumeText = request.resumeText();
        }
        if (analysis == null && request.analysis() != null) {
            analysis = request.analysis();
        }

        if (resumeText == null || resumeText.isBlank() || analysis == null) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("error", "Analysis session expired or not found. Please re-analyze your resume."))
            );
        }

        final String finalResumeText = resumeText;
        final ResumeAnalysisDTO finalAnalysis = analysis;
        final String analysisId = request.analysisId() != null ? request.analysisId() : "direct";

        logger.info("improvement_started analysisId={}", analysisId);

        CompletableFuture<ResponseEntity<?>> future = new CompletableFuture<>();

        try {
            analysisExecutor.submit(() -> {
                MDC.put("analysisId", analysisId);
                try {
                    final String jobDesc = request.jobDescription() != null && !request.jobDescription().isBlank()
                            ? request.jobDescription().strip()
                            : (sessionStore.get(analysisId).map(s -> s.jobDescription()).orElse(null));
                    AIImprovementResult improvementResult = aiProvider.improveResumeWithProvider(finalResumeText, finalAnalysis, jobDesc);
                    logger.info("improvement_succeeded analysisId={} provider={} mode={}", analysisId, improvementResult.providerName(),
                            jobDesc != null ? "job_specific" : "general");
                    future.complete(ResponseEntity.ok()
                            .header("X-AI-Provider", improvementResult.providerName())
                            .body(improvementResult.improvement()));
                } catch (AnalysisContractValidationException e) {
                    logger.warn("improvement_failed analysisId={} category={} reason={}",
                            analysisId, AnalysisErrorMessages.Category.AI_VALIDATION, e.getReason());
                    future.complete(ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                            .body(Map.of("error", AnalysisErrorMessages.forImprovementException(e))));
                } catch (GeminiResponseException e) {
                    logger.warn("improvement_failed analysisId={} category={} reason={}",
                            analysisId, AnalysisErrorMessages.Category.AI_RESPONSE, e.getReason());
                    future.complete(ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                            .body(Map.of("error", AnalysisErrorMessages.forImprovementException(e))));
                } catch (AICommunicationException e) {
                    logger.error("improvement_failed analysisId={} category={} provider={}",
                            analysisId, AnalysisErrorMessages.Category.AI_COMMUNICATION, e.getProvider());
                    future.complete(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                            .body(Map.of("error", AnalysisErrorMessages.forImprovementException(e))));
                } catch (Exception e) {
                    logger.error("improvement_failed analysisId={} category={} errorType={}",
                            analysisId, AnalysisErrorMessages.Category.INTERNAL, e.getClass().getName());
                    future.complete(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("error", AnalysisErrorMessages.forImprovementCategory(AnalysisErrorMessages.Category.INTERNAL))));
                } finally {
                    MDC.remove("analysisId");
                }
            });
        } catch (RejectedExecutionException e) {
            logger.warn("improvement_rejected analysisId={} category={}",
                    analysisId, AnalysisErrorMessages.Category.BUSY);
            return CompletableFuture.completedFuture(
                    ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                            .body(Map.of("error", AnalysisErrorMessages.forImprovementCategory(AnalysisErrorMessages.Category.BUSY)))
            );
        }

        return future.orTimeout(analysisTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    logger.warn("improvement_timed_out analysisId={}", analysisId);
                    return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                            .body(Map.of("error", AnalysisErrorMessages.forImprovementCategory(AnalysisErrorMessages.Category.TIMEOUT)));
                });
    }

    /* =========================================
       ANALYSIS STATUS & RECOVERY
       ========================================= */

    @GetMapping(
            value = "/analysis/{analysisId}/status",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseBody
    public ResponseEntity<?> getAnalysisStatus(@PathVariable("analysisId") String analysisId) {
        if (analysisId == null || analysisId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid analysis ID."));
        }
        return sessionStore.getStatus(analysisId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Analysis not found or expired.")));
    }

    @PostMapping(
            value = "/analysis/{analysisId}/cancel",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseBody
    public ResponseEntity<?> cancelAnalysis(@PathVariable("analysisId") String analysisId) {
        if (analysisId == null || analysisId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid analysis ID."));
        }
        AnalysisRequestState req = activeRequests.remove(analysisId);
        if (req != null) {
            req.cancel();
            sessionStore.cancelSession(analysisId);
            logger.info("analysis_explicitly_cancelled analysisId={}", analysisId);
            return ResponseEntity.ok(Map.of("analysisId", analysisId, "status", "CANCELLED"));
        }
        var statusOpt = sessionStore.getStatus(analysisId);
        if (statusOpt.isPresent()) {
            sessionStore.cancelSession(analysisId);
            return ResponseEntity.ok(Map.of("analysisId", analysisId, "status", "CANCELLED"));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Analysis not found."));
    }

    AnalysisSessionStore getSessionStore() {
        return sessionStore;
    }

    Map<String, AnalysisRequestState> getActiveRequests() {
        return activeRequests;
    }

}
