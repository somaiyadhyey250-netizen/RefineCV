package com.resumeanalyzer.resume_analyzer;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Controller handling the standalone Interview Prep from CV product flow.
 */
@Controller
public class InterviewPrepController {

    private static final Logger logger = LoggerFactory.getLogger(InterviewPrepController.class);

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
    public InterviewPrepController(
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
        this.resumeTextExtractor = resumeTextExtractor;
        this.aiProvider = aiProvider;
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

    /* =========================================
       PRODUCT PAGES
    ========================================= */

    @GetMapping("/interview-prep")
    public String interviewPrepPage() {
        return "interview-prep";
    }

    @GetMapping("/interview-prep/{interviewPrepId}")
    public String interviewPrepResultPage(@PathVariable("interviewPrepId") String interviewPrepId, Model model) {
        model.addAttribute("prepId", interviewPrepId);
        sessionStore.getInterviewPrep(interviewPrepId).ifPresent(prep -> {
            model.addAttribute("prepData", prep);
        });
        return "interview-prep-result";
    }

    @GetMapping(value = "/interview-prep/{interviewPrepId}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> getInterviewPrepStatus(@PathVariable("interviewPrepId") String interviewPrepId) {
        return sessionStore.getInterviewPrep(interviewPrepId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Interview prep session not found or expired.")));
    }

    /* =========================================
       SSE INTERVIEW PREP GENERATION
    ========================================= */

    @PostMapping(
            value = "/interview-prep",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    @ResponseBody
    public SseEmitter generateInterviewPrep(
            @RequestParam(value = "resume", required = false) MultipartFile resume,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        SseEmitter emitter = new SseEmitter(
                analysisTimeout.plus(sseTimeoutGrace).toMillis()
        );
        AnalysisRequestState state = new AnalysisRequestState("prep-" + java.util.UUID.randomUUID().toString());
        if (response != null) {
            response.setHeader("X-Interview-Prep-Id", state.getAnalysisId());
        }
        activeRequests.put(state.getAnalysisId(), state);

        logger.info("interview_prep_request_started prepId={} uploadBytes={}",
                state.getAnalysisId(), resume == null ? 0 : resume.getSize());

        if (!rateLimiter.tryAcquire(request.getRemoteAddr())) {
            logger.warn("interview_prep_request_rejected prepId={} category={}",
                    state.getAnalysisId(), AnalysisErrorMessages.Category.RATE_LIMITED);
            finishError(emitter, state,
                    AnalysisErrorMessages.forCategory(AnalysisErrorMessages.Category.RATE_LIMITED));
            return emitter;
        }

        try {
            validateUpload(resume);

            Future<?> task = analysisExecutor.submit(() -> runInterviewPrep(resume, emitter, state));
            state.setTask(task);

            ScheduledFuture<?> timeout = timeoutScheduler.schedule(
                    () -> handleTimeout(emitter, state),
                    Instant.now().plus(analysisTimeout)
            );
            if (timeout != null) {
                state.setTimeout(timeout);
            }
        } catch (ResumeValidationException e) {
            logger.warn("interview_prep_request_rejected prepId={} category={} reason={}",
                    state.getAnalysisId(), AnalysisErrorMessages.classify(e), e.getReason());
            finishError(emitter, state, AnalysisErrorMessages.forException(e));
        } catch (RejectedExecutionException e) {
            logger.warn("interview_prep_request_rejected prepId={} category={}",
                    state.getAnalysisId(), AnalysisErrorMessages.Category.BUSY);
            finishError(emitter, state, AnalysisErrorMessages.forCategory(AnalysisErrorMessages.Category.BUSY));
        } catch (RuntimeException e) {
            logger.error("interview_prep_start_failed prepId={} errorType={}",
                    state.getAnalysisId(), e.getClass().getName());
            state.cancel();
            finishError(emitter, state, AnalysisErrorMessages.forCategory(AnalysisErrorMessages.Category.INTERNAL));
        }

        return emitter;
    }

    void runInterviewPrep(
            MultipartFile file,
            SseEmitter emitter,
            AnalysisRequestState state
    ) {
        final String prepId = state.getAnalysisId();
        MDC.put("analysisId", prepId);
        final String filename = (file != null && file.getOriginalFilename() != null && !file.getOriginalFilename().isBlank())
                ? file.getOriginalFilename() : "Resume.pdf";

        logger.info("interview_prep_started prepId={} filename={}", prepId, filename);
        try {
            state.checkActive();
            sendEvent(emitter, state, "prep-id", prepId);
            sendStatus(emitter, state, "upload");

            byte[] pdfBytes = file.getBytes();
            state.checkActive();

            sendStatus(emitter, state, "extracting");
            String resumeText = null;
            try {
                resumeText = resumeTextExtractor.extractText(pdfBytes, status -> sendStatus(emitter, state, "extracting"));
            } catch (Exception e) {
                logger.warn("interview_prep_extraction_failed prepId={}", prepId);
            }

            state.checkActive();

            boolean unreadable = resumeText == null || resumeText.strip().length() < 50;
            if (unreadable) {
                InterviewPrepDTO unreadableDto = InterviewPrepDTO.forUnreadable(
                        prepId, filename, "The resume could not be reliably read to extract experience and claims.");
                sessionStore.saveInterviewPrep(unreadableDto);
                sendEvent(emitter, state, "result", unreadableDto);
                emitter.complete();
                logger.info("interview_prep_unreadable prepId={}", prepId);
                return;
            }

            sessionStore.savePrepResumeText(prepId, resumeText);

            sendStatus(emitter, state, "generating");
            AIInterviewPrepResult aiResult = aiProvider.generateInterviewPrepWithProvider(
                    resumeText, prepId, filename, status -> sendStatus(emitter, state, status)
            );

            InterviewPrepDTO prep = aiResult.prep();
            sessionStore.saveInterviewPrep(prep);

            if (aiResult.providerName() != null && !aiResult.providerName().isBlank()) {
                sendEvent(emitter, state, "provider", aiResult.providerName());
            }
            sendEvent(emitter, state, "result", prep);
            emitter.complete();
            logger.info("interview_prep_succeeded prepId={} provider={} questions={} durationMs={}",
                    prepId, aiResult.providerName(), prep.questionCount(), state.elapsedMillis());

        } catch (CancellationException e) {
            logger.info("interview_prep_cancelled prepId={} durationMs={}", prepId, state.elapsedMillis());
            finishError(emitter, state, AnalysisErrorMessages.forException(e));
        } catch (Exception e) {
            if (state.isCancelled() || Thread.currentThread().isInterrupted()) {
                return;
            }
            logger.error("interview_prep_failed prepId={} errorType={} durationMs={}",
                    prepId, e.getClass().getName(), state.elapsedMillis());
            finishError(emitter, state, AnalysisErrorMessages.forCategory(AnalysisErrorMessages.Category.INTERNAL));
        } finally {
            activeRequests.remove(prepId);
            state.cancelTimeout();
            MDC.remove("analysisId");
        }
    }

    /* =========================================
       GENERATE MORE QUESTIONS API
    ========================================= */

    @PostMapping(
            value = "/api/interview-prep/{interviewPrepId}/more-questions",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseBody
    public ResponseEntity<?> generateMoreQuestions(
            @PathVariable("interviewPrepId") String interviewPrepId,
            HttpServletRequest request
    ) {
        if (!rateLimiter.tryAcquire(request.getRemoteAddr())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", AnalysisErrorMessages.forCategory(AnalysisErrorMessages.Category.RATE_LIMITED)));
        }

        var prepOpt = sessionStore.getInterviewPrep(interviewPrepId);
        if (prepOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Interview prep session not found."));
        }

        InterviewPrepDTO prep = prepOpt.get();
        if (prep.questionCount() >= 15) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Maximum limit of 15 questions reached.", "capped", true));
        }

        var textOpt = sessionStore.getPrepResumeText(interviewPrepId);
        if (textOpt.isEmpty() || textOpt.get().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Resume content not available for additional generation."));
        }

        try {
            List<String> existingQuestions = prep.questions().stream()
                    .map(InterviewQuestionDTO::question)
                    .toList();

            List<InterviewQuestionDTO> additional = aiProvider.generateMoreQuestions(textOpt.get(), existingQuestions);
            InterviewPrepDTO updated = prep.withMergedQuestions(additional);
            sessionStore.saveInterviewPrep(updated);

            logger.info("generate_more_questions_success prepId={} newTotal={}", interviewPrepId, updated.questionCount());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "prep", updated,
                    "addedCount", updated.questionCount() - prep.questionCount(),
                    "totalQuestions", updated.questionCount(),
                    "capped", updated.questionCount() >= 15
            ));
        } catch (Exception e) {
            logger.error("generate_more_questions_failed prepId={} error={}", interviewPrepId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to generate additional questions. Please try again."));
        }
    }

    /* =========================================
       PRACTICE ANSWER EVALUATION API
    ========================================= */

    public record PracticeAnswerRequest(
            String questionId,
            String question,
            String basedOn,
            String userAnswer
    ) {}

    @PostMapping(
            value = "/api/interview-prep/{interviewPrepId}/evaluate-answer",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseBody
    public ResponseEntity<?> evaluateAnswer(
            @PathVariable("interviewPrepId") String interviewPrepId,
            @RequestBody(required = false) PracticeAnswerRequest request,
            HttpServletRequest httpRequest
    ) {
        if (!rateLimiter.tryAcquire(httpRequest.getRemoteAddr())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", AnalysisErrorMessages.forCategory(AnalysisErrorMessages.Category.RATE_LIMITED)));
        }

        if (request == null || request.userAnswer() == null || request.userAnswer().strip().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Answer text must not be empty."));
        }

        if (request.userAnswer().strip().length() > 4000) {
            return ResponseEntity.badRequest().body(Map.of("error", "Answer exceeds the maximum allowed length of 4000 characters."));
        }

        try {
            AIAnswerEvaluationResult result = aiProvider.evaluateAnswerWithProvider(
                    request.question(),
                    request.basedOn(),
                    request.userAnswer()
            );

            return ResponseEntity.ok()
                    .header("X-AI-Provider", result.providerName())
                    .body(result.evaluation());
        } catch (Exception e) {
            logger.error("evaluate_answer_failed prepId={} error={}", interviewPrepId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to evaluate practice answer. Please try again."));
        }
    }

    /* =========================================
       HELPERS
    ========================================= */

    private void validateUpload(MultipartFile resume) {
        if (resume == null || resume.isEmpty()) {
            throw new ResumeValidationException(ResumeValidationException.Reason.INVALID_UPLOAD);
        }

        if (resume.getSize() > maxUploadBytes) {
            throw new ResumeValidationException(ResumeValidationException.Reason.FILE_TOO_LARGE);
        }
    }

    private void sendStatus(SseEmitter emitter, AnalysisRequestState state, String status) {
        state.checkActive();
        state.setStage(status);
        logger.info("interview_prep_stage prepId={} stage={}", state.getAnalysisId(), status);
        sendEvent(emitter, state, "status", status);
    }

    private boolean sendEvent(SseEmitter emitter, AnalysisRequestState state, String eventName, Object data) {
        if (state.isCancelled() || Thread.currentThread().isInterrupted()) {
            return false;
        }
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
            return true;
        } catch (IOException | IllegalStateException e) {
            logger.debug("sse_send_failed prepId={} event={}", state.getAnalysisId(), eventName);
            state.cancel();
            return false;
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
        logger.warn("interview_prep_timed_out prepId={} durationMs={}", state.getAnalysisId(), state.elapsedMillis());
        sendEvent(emitter, state, "error",
                AnalysisErrorMessages.forCategory(AnalysisErrorMessages.Category.TIMEOUT));
        state.cancel();
        activeRequests.remove(state.getAnalysisId());
        try {
            emitter.complete();
        } catch (Exception ignored) {}
    }

    public AnalysisSessionStore getSessionStore() {
        return sessionStore;
    }
}
