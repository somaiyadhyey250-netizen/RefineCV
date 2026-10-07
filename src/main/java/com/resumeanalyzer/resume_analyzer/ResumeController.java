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
import org.springframework.web.bind.annotation.DeleteMapping;
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
import org.springframework.ui.Model;

@Controller
public class ResumeController {

    private static final Logger logger = LoggerFactory.getLogger(ResumeController.class);

    /** Maximum number of characters accepted in a job description (matches frontend maxlength). */
    static final int JD_MAX_LENGTH = 8000;




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

    @Value("${refinecv.app.base-url:}")
    private String configuredBaseUrl = "";


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
       PRODUCT PAGES & SEO
    ========================================= */

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public String robotsTxt() {
        StringBuilder sb = new StringBuilder();
        sb.append("# RefineCV Robots Policy\n");
        sb.append("User-agent: *\n");
        sb.append("Allow: /\n");
        sb.append("Disallow: /api/\n");
        if (configuredBaseUrl != null && !configuredBaseUrl.isBlank()) {
            sb.append("\nSitemap: ").append(configuredBaseUrl.trim().replaceAll("/+$", "")).append("/sitemap.xml\n");
        }
        return sb.toString();
    }

    @GetMapping(value = "/sitemap.xml", produces = "application/xml;charset=UTF-8")
    @ResponseBody
    public String sitemapXml(HttpServletRequest request) {
        String baseUrl = resolveBaseUrl(request);
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n" +
                "  <url>\n" +
                "    <loc>" + baseUrl + "/</loc>\n" +
                "    <changefreq>weekly</changefreq>\n" +
                "    <priority>1.0</priority>\n" +
                "  </url>\n" +
                "  <url>\n" +
                "    <loc>" + baseUrl + "/analyze</loc>\n" +
                "    <changefreq>weekly</changefreq>\n" +
                "    <priority>0.9</priority>\n" +
                "  </url>\n" +
                "  <url>\n" +
                "    <loc>" + baseUrl + "/compare</loc>\n" +
                "    <changefreq>weekly</changefreq>\n" +
                "    <priority>0.9</priority>\n" +
                "  </url>\n" +
                "  <url>\n" +
                "    <loc>" + baseUrl + "/interview-prep</loc>\n" +
                "    <changefreq>weekly</changefreq>\n" +
                "    <priority>0.9</priority>\n" +
                "  </url>\n" +
                "</urlset>";
    }

    private String resolveBaseUrl(HttpServletRequest request) {
        if (configuredBaseUrl != null && !configuredBaseUrl.isBlank()) {
            return configuredBaseUrl.trim().replaceAll("/+$", "");
        }
        if (request == null) {
            return "";
        }
        String proto = request.getHeader("X-Forwarded-Proto");
        String scheme = (proto != null && !proto.isBlank()) ? proto.trim() : request.getScheme();
        String host = request.getHeader("X-Forwarded-Host");
        if (host == null || host.isBlank()) {
            host = request.getHeader("Host");
        }
        if (host != null && !host.isBlank()) {
            return scheme + "://" + host.trim();
        }
        int port = request.getServerPort();
        boolean isDefault = ("http".equalsIgnoreCase(scheme) && port == 80) || ("https".equalsIgnoreCase(scheme) && port == 443);
        return scheme + "://" + request.getServerName() + (isDefault ? "" : ":" + port);
    }

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/analyze")
    public String analyzePage() {
        return "analyze";
    }

    @GetMapping("/analysis/{analysisId}")
    public String generalAnalysisPage(@PathVariable("analysisId") String analysisId, Model model) {
        model.addAttribute("analysisId", analysisId);
        model.addAttribute("mode", "GENERAL");
        sessionStore.get(analysisId).ifPresent(s -> {
            model.addAttribute("sessionData", s);
            model.addAttribute("analysisResult", s.analysis());
        });
        return "analysis";
    }

    @GetMapping("/job-analysis/{analysisId}")
    public String jobAnalysisPage(@PathVariable("analysisId") String analysisId, Model model) {
        model.addAttribute("analysisId", analysisId);
        model.addAttribute("mode", "SPECIFIC_JOB");
        sessionStore.get(analysisId).ifPresent(s -> {
            model.addAttribute("sessionData", s);
            model.addAttribute("analysisResult", s.analysis());
            model.addAttribute("jobDescription", s.jobDescription());
        });
        return "job-analysis";
    }

    @GetMapping("/improve/{analysisId}")
    public String improvePage(@PathVariable("analysisId") String analysisId, Model model) {
        model.addAttribute("analysisId", analysisId);
        String resolvedMode = "GENERAL";
        var sessionOpt = sessionStore.get(analysisId);
        if (sessionOpt.isPresent()) {
            var s = sessionOpt.get();
            model.addAttribute("sessionData", s);
            model.addAttribute("analysisResult", s.analysis());
            model.addAttribute("jobDescription", s.jobDescription());
            if (s.mode() != null) {
                resolvedMode = s.mode().name();
            }
        }
        model.addAttribute("mode", resolvedMode);
        sessionStore.getImprovement(analysisId).ifPresent(imp -> {
            model.addAttribute("improvementResult", imp);
        });

        return "improve";
    }

    @GetMapping("/resume/{analysisId}")
    public String resumeWorkspacePage(@PathVariable("analysisId") String analysisId, Model model) {
        model.addAttribute("analysisId", analysisId);
        String resolvedMode = "GENERAL";
        var sessionOpt = sessionStore.get(analysisId);
        if (sessionOpt.isPresent()) {
            var s = sessionOpt.get();
            model.addAttribute("sessionData", s);
            model.addAttribute("resumeText", s.resumeText());
            if (s.mode() != null) {
                resolvedMode = s.mode().name();
            }
        }
        model.addAttribute("mode", resolvedMode);
        return "resume";
    }

    @GetMapping("/history")
    public String historyPage() {
        return "history";
    }

    @GetMapping("/compare")
    public String comparePage() {
        return "compare";
    }

    @GetMapping("/compare/{comparisonId}")
    public String compareResultPage(@PathVariable("comparisonId") String comparisonId, Model model) {
        model.addAttribute("comparisonId", comparisonId);
        sessionStore.getComparison(comparisonId).ifPresent(c -> {
            model.addAttribute("comparisonResult", c);
            model.addAttribute("jobDescription", c.jobDescription());
        });
        return "compare-result";
    }

    @GetMapping(value = "/api/history", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public java.util.List<HistoryItemDTO> getHistoryApi() {
        return sessionStore.getRecentCompletedSessions();
    }

    @DeleteMapping(value = "/api/history", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> clearHistoryApi() {
        try {
            sessionStore.clearHistory();
            return ResponseEntity.ok(Map.of("success", true, "message", "History cleared"));
        } catch (Exception e) {
            logger.error("Failed to clear history: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Failed to clear history"));
        }
    }

    @GetMapping(value = "/compare/{comparisonId}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> getComparisonStatus(@PathVariable("comparisonId") String comparisonId) {
        return sessionStore.getComparison(comparisonId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
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

        try {
            final AnalysisMode resolvedMode = AnalysisMode.fromString(mode);
            validateModeAndJobDescription(resolvedMode, jobDescription);

            final String resolvedJobDesc = (resolvedMode == AnalysisMode.SPECIFIC_JOB)
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
        } catch (ResumeValidationException e) {
            logger.warn("analysis_request_rejected analysisId={} category={} reason={}",
                    state.getAnalysisId(), AnalysisErrorMessages.classify(e), e.getReason());
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

    /**
     * Validates the analysis mode and job description.
     * SPECIFIC_JOB requires a non-null, non-blank, and bounded-length job description.
     * GENERAL allows null or blank JD.
     */
    void validateModeAndJobDescription(AnalysisMode mode, String jobDescription) {
        if (mode == AnalysisMode.SPECIFIC_JOB) {
            if (jobDescription == null || jobDescription.isBlank()) {
                throw new ResumeValidationException(ResumeValidationException.Reason.MISSING_JOB_DESCRIPTION);
            }
            String stripped = jobDescription.strip();
            if (stripped.length() > JD_MAX_LENGTH) {
                throw new ResumeValidationException(ResumeValidationException.Reason.JOB_DESCRIPTION_TOO_LONG);
            }
            if (!JobDescriptionValidator.isValid(stripped)) {
                throw new ResumeValidationException(ResumeValidationException.Reason.INSUFFICIENT_JOB_DESCRIPTION);
            }
        } else if (mode == AnalysisMode.GENERAL) {
            // JD is ignored for GENERAL; no validation required.
            if (jobDescription != null && jobDescription.strip().length() > JD_MAX_LENGTH) {
                throw new ResumeValidationException(ResumeValidationException.Reason.JOB_DESCRIPTION_TOO_LONG);
            }
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
            String originalFilename = resume != null && resume.getOriginalFilename() != null && !resume.getOriginalFilename().isBlank()
                    ? resume.getOriginalFilename()
                    : "Resume.pdf";
            sessionStore.completeSession(state.getAnalysisId(), resumeText, analysis, aiResult.providerName(), effectiveMode, jobDescription, originalFilename);
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
            String causeType = e.getCause() == null ? "none" : e.getCause().getClass().getName();
            String causeMessage = e.getCause() == null ? "none" : e.getCause().getMessage();
            logger.error("analysis_failed analysisId={} stage={} category={} causeType={} causeMessage=\"{}\" durationMs={}",
                    state.getAnalysisId(), e.getStage(), AnalysisErrorMessages.Category.PROCESSING,
                    causeType, causeMessage, state.elapsedMillis(), e);
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

                            .data(data, (data instanceof ResumeAnalysisDTO || data instanceof ResumeComparisonDTO)
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
        if (request == null) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("error", "Invalid improvement request."))
            );
        }

        // 1. If an improvement has already been generated for this analysis session, return it immediately without rate-limiting.
        final String requestedAnalysisId = request.analysisId() != null && !request.analysisId().isBlank() ? request.analysisId() : "direct";
        var cachedImprovement = sessionStore.getImprovement(requestedAnalysisId);
        if (cachedImprovement.isPresent()) {
            logger.info("improvement_cache_hit analysisId={}", requestedAnalysisId);
            return CompletableFuture.completedFuture(
                    ResponseEntity.ok()
                            .header("X-AI-Provider", "cached")
                            .body(cachedImprovement.get())
            );
        }

        // 2. Only rate limit fresh AI generation calls
        if (!rateLimiter.tryAcquire(httpRequest.getRemoteAddr())) {
            logger.warn("improvement_request_rejected category={}", AnalysisErrorMessages.Category.RATE_LIMITED);
            return CompletableFuture.completedFuture(
                    ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                            .body(Map.of("error", AnalysisErrorMessages.forImprovementCategory(AnalysisErrorMessages.Category.RATE_LIMITED)))
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
        final String analysisId = requestedAnalysisId;

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
                    sessionStore.saveImprovement(analysisId, improvementResult.improvement());
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

    /* =========================================
       RESUME COMPARISON (V4.3)
    ========================================= */

    @PostMapping(
            value = "/compare",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    @ResponseBody
    public SseEmitter compareResumes(
            @RequestParam(value = "resumeA", required = false)
            MultipartFile resumeA,
            @RequestParam(value = "resumeB", required = false)
            MultipartFile resumeB,
            @RequestParam(value = "jobDescription", required = false)
            String jobDescription,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        SseEmitter emitter = new SseEmitter(
                analysisTimeout.plus(sseTimeoutGrace).toMillis()
        );
        AnalysisRequestState state = new AnalysisRequestState();
        if (response != null) {
            response.setHeader("X-Comparison-Id", state.getAnalysisId());
        }
        activeRequests.put(state.getAnalysisId(), state);

        logger.info("comparison_request_started comparisonId={} fileA={} fileB={}",
                state.getAnalysisId(),
                resumeA == null ? 0 : resumeA.getSize(),
                resumeB == null ? 0 : resumeB.getSize());

        if (!rateLimiter.tryAcquire(request.getRemoteAddr())) {
            logger.warn("comparison_request_rejected comparisonId={} category={}",
                    state.getAnalysisId(), AnalysisErrorMessages.Category.RATE_LIMITED);
            finishError(emitter, state,
                    AnalysisErrorMessages.forCategory(AnalysisErrorMessages.Category.RATE_LIMITED));
            return emitter;
        }

        try {
            if (resumeA == null || resumeA.isEmpty() || resumeB == null || resumeB.isEmpty()) {
                throw new ResumeValidationException(ResumeValidationException.Reason.INVALID_UPLOAD);
            }
            validateUpload(resumeA);
            validateUpload(resumeB);

            if (jobDescription != null && jobDescription.strip().length() > JD_MAX_LENGTH) {
                throw new ResumeValidationException(ResumeValidationException.Reason.JOB_DESCRIPTION_TOO_LONG);
            }

            final String resolvedJobDesc = (jobDescription != null && !jobDescription.isBlank())
                    ? jobDescription.strip()
                    : null;

            Future<?> task = analysisExecutor.submit(() -> runComparison(resumeA, resumeB, emitter, state, resolvedJobDesc));
            state.setTask(task);

            ScheduledFuture<?> timeout = timeoutScheduler.schedule(
                    () -> handleTimeout(emitter, state),
                    Instant.now().plus(analysisTimeout)
            );
            if (timeout != null) {
                state.setTimeout(timeout);
            }
        } catch (ResumeValidationException e) {
            logger.warn("comparison_request_rejected comparisonId={} reason={}",
                    state.getAnalysisId(), e.getReason());
            finishError(emitter, state, AnalysisErrorMessages.forException(e));
        } catch (RejectedExecutionException e) {
            logger.warn("comparison_request_rejected comparisonId={} category={}",
                    state.getAnalysisId(), AnalysisErrorMessages.Category.BUSY);
            finishError(emitter, state, AnalysisErrorMessages.forCategory(AnalysisErrorMessages.Category.BUSY));
        } catch (RuntimeException e) {
            logger.error("comparison_start_failed comparisonId={} errorType={}",
                    state.getAnalysisId(), e.getClass().getName());
            state.cancel();
            finishError(emitter, state, AnalysisErrorMessages.forCategory(AnalysisErrorMessages.Category.INTERNAL));
        }

        return emitter;
    }

    void runComparison(
            MultipartFile fileA,
            MultipartFile fileB,
            SseEmitter emitter,
            AnalysisRequestState state,
            String jobDescription
    ) {
        final String comparisonId = state.getAnalysisId();
        MDC.put("analysisId", comparisonId);
        final String nameA = (fileA.getOriginalFilename() != null && !fileA.getOriginalFilename().isBlank())
                ? fileA.getOriginalFilename() : "Resume A.pdf";
        final String nameB = (fileB.getOriginalFilename() != null && !fileB.getOriginalFilename().isBlank())
                ? fileB.getOriginalFilename() : "Resume B.pdf";

        logger.info("comparison_started comparisonId={} fileA={} fileB={}", comparisonId, nameA, nameB);
        try {
            state.checkActive();
            sendEvent(emitter, state, "comparison-id", comparisonId);
            sendStatus(emitter, state, "comparison-upload");
            sendStatus(emitter, state, "upload");

            byte[] bytesA = fileA.getBytes();
            byte[] bytesB = fileB.getBytes();
            state.checkActive();

            sendStatus(emitter, state, "comparison-extract-a");
            sendStatus(emitter, state, "extracting_a");
            String textA = null;
            try {
                textA = resumeTextExtractor.extractText(bytesA, status -> {
                    if (status != null && status.startsWith("ocr")) {
                        sendStatus(emitter, state, "comparison-ocr");
                        sendStatus(emitter, state, status);
                    } else {
                        sendStatus(emitter, state, "comparison-extract-a");
                    }
                });
            } catch (Exception e) {
                logger.warn("comparison_extraction_failed_a comparisonId={}", comparisonId);
            }

            state.checkActive();

            sendStatus(emitter, state, "comparison-extract-b");
            sendStatus(emitter, state, "extracting_b");
            String textB = null;
            try {
                textB = resumeTextExtractor.extractText(bytesB, status -> {
                    if (status != null && status.startsWith("ocr")) {
                        sendStatus(emitter, state, "comparison-ocr");
                        sendStatus(emitter, state, status);
                    } else {
                        sendStatus(emitter, state, "comparison-extract-b");
                    }
                });
            } catch (Exception e) {
                logger.warn("comparison_extraction_failed_b comparisonId={}", comparisonId);
            }

            state.checkActive();

            boolean unreadableA = textA == null || textA.strip().length() < 50;
            boolean unreadableB = textB == null || textB.strip().length() < 50;
            if (unreadableA || unreadableB) {
                String reason;
                if (unreadableA && unreadableB) {
                    reason = "Both Resume A and Resume B could not be reliably read";
                } else if (unreadableA) {
                    reason = "Resume A (" + nameA + ") could not be reliably read";
                } else {
                    reason = "Resume B (" + nameB + ") could not be reliably read";
                }
                ResumeComparisonDTO unreadableDto = ResumeComparisonDTO.forUnreadable(
                        comparisonId, nameA, nameB, reason, jobDescription);
                sessionStore.saveComparison(unreadableDto);
                sendStatus(emitter, state, "comparison-complete");
                sendStatus(emitter, state, "completed");
                sendEvent(emitter, state, "result", unreadableDto);
                emitter.complete();
                logger.info("comparison_unreadable comparisonId={}", comparisonId);
                return;
            }

            String normA = textA.replaceAll("\\s+", " ").trim().toLowerCase();
            String normB = textB.replaceAll("\\s+", " ").trim().toLowerCase();
            if (normA.equals(normB)) {
                ResumeComparisonDTO identicalDto = ResumeComparisonDTO.forIdentical(
                        comparisonId, nameA, nameB, jobDescription);
                sessionStore.saveComparison(identicalDto);
                sendStatus(emitter, state, "comparison-complete");
                sendStatus(emitter, state, "completed");
                sendEvent(emitter, state, "result", identicalDto);
                emitter.complete();
                logger.info("comparison_identical comparisonId={}", comparisonId);
                return;
            }

            sendStatus(emitter, state, "comparison-ai");
            sendStatus(emitter, state, "comparing");
            AIComparisonResult aiResult = aiProvider.compareResumesWithProvider(
                    textA, textB, jobDescription, comparisonId, nameA, nameB,
                    status -> {
                        if ("ai-fallback".equals(status)) {
                            sendStatus(emitter, state, "comparison-fallback");
                            sendStatus(emitter, state, "ai-fallback");
                        } else {
                            sendStatus(emitter, state, "comparison-ai");
                            sendStatus(emitter, state, status);
                        }
                    }
            );

            sendStatus(emitter, state, "comparison-scorecard");
            sendStatus(emitter, state, "writing");
            ResumeComparisonDTO rawComparison = aiResult.comparison();

            ResumeComparisonDTO finalComparison = new ResumeComparisonDTO(
                    rawComparison.comparisonId(),
                    rawComparison.fileNameA(),
                    rawComparison.fileNameB(),
                    rawComparison.totalScoreA(),
                    rawComparison.totalScoreB(),
                    rawComparison.scoreDifference(),
                    rawComparison.winner(),
                    rawComparison.verdictTitle(),
                    rawComparison.verdictExplanation(),
                    rawComparison.keyDifferentiators(),
                    rawComparison.categories(),
                    rawComparison.overallTakeaway(),
                    rawComparison.resumeABorrowsFromB(),
                    rawComparison.resumeBBorrowsFromA(),
                    false,
                    false,
                    null,
                    rawComparison.jobDescription(),
                    rawComparison.jobContext(),
                    null,
                    null,
                    aiResult.providerName(),
                    rawComparison.createdAt()
            );

            sessionStore.saveComparison(finalComparison);

            if (aiResult.providerName() != null && !aiResult.providerName().isBlank()) {
                sendEvent(emitter, state, "provider", aiResult.providerName());
            }
            sendStatus(emitter, state, "comparison-complete");
            sendStatus(emitter, state, "completed");
            sendEvent(emitter, state, "result", finalComparison);
            emitter.complete();
            logger.info("comparison_succeeded comparisonId={} provider={} durationMs={}",
                    comparisonId, aiResult.providerName(), state.elapsedMillis());

        } catch (CancellationException e) {
            logger.info("comparison_cancelled comparisonId={} durationMs={}", comparisonId, state.elapsedMillis());
            finishError(emitter, state, AnalysisErrorMessages.forException(e));
        } catch (Exception e) {
            if (state.isCancelled() || Thread.currentThread().isInterrupted()) {
                return;
            }
            logger.error("comparison_failed comparisonId={} errorType={} durationMs={}",
                    comparisonId, e.getClass().getName(), state.elapsedMillis());
            finishError(emitter, state, AnalysisErrorMessages.forCategory(AnalysisErrorMessages.Category.INTERNAL));
        } finally {
            activeRequests.remove(comparisonId);
            state.cancelTimeout();
            MDC.remove("analysisId");
        }
    }

    AnalysisSessionStore getSessionStore() {
        return sessionStore;
    }

    Map<String, AnalysisRequestState> getActiveRequests() {
        return activeRequests;
    }

}
