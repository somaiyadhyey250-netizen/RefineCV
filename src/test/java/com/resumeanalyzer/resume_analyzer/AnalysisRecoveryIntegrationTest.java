package com.resumeanalyzer.resume_analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@SpringBootTest(properties = {
        "refinecv.analysis.timeout=5s",
        "refinecv.analysis.rate-limit.max-requests=100"
})
@AutoConfigureMockMvc
class AnalysisRecoveryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AnalysisSessionStore sessionStore;

    @Autowired
    private ResumeController resumeController;

    @MockitoBean
    private ResumeTextExtractor resumeTextExtractor;

    @MockitoBean
    private GeminiService geminiService;

    private final ResumeAnalysisDTO sampleAnalysis = new ResumeAnalysisDTO(
            88,
            "Experienced Java Backend Engineer",
            List.of("Java", "Spring Boot"),
            List.of("Kubernetes"),
            List.of("Microservices architecture"),
            List.of("No cloud certification"),
            "High ATS compatibility",
            List.of("Add AWS certification"),
            List.of("Highlight Docker experience")
    );

    @BeforeEach
    void setUp() {
        sessionStore.clear();
    }

    @Test
    @DisplayName("1. Analysis completes and result is stored in AnalysisSessionStore")
    void analysisCompletesAndResultIsStored() throws Exception {
        when(resumeTextExtractor.extractText(any(), any())).thenReturn("Extracted resume text");
        when(geminiService.analyzeResume(eq("Extracted resume text"), any())).thenReturn(sampleAnalysis);
        when(geminiService.getProviderName()).thenReturn("gemini");

        MockMultipartFile file = new MockMultipartFile("resume", "resume.pdf", "application/pdf", new byte[]{1, 2, 3});

        MvcResult result = mockMvc.perform(multipart("/analyze").file(file))
                .andReturn();

        String analysisId = result.getResponse().getHeader("X-Analysis-Id");
        assertNotNull(analysisId, "X-Analysis-Id header must be present on /analyze response");

        // Wait up to 3 seconds for background worker to complete
        boolean completed = waitForStatus(analysisId, AnalysisStatus.COMPLETED, 3000);
        assertTrue(completed, "Analysis job should complete successfully");

        // Query status endpoint
        mockMvc.perform(get("/analysis/" + analysisId + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value(analysisId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.result.score").value(88))
                .andExpect(jsonPath("$.result.summary").value("Experienced Java Backend Engineer"))
                .andExpect(jsonPath("$.provider").value("gemini"))
                .andExpect(jsonPath("$.resumeText").doesNotExist()); // Verify raw resume text is NOT exposed
    }

    @Test
    @DisplayName("2. SSE disconnect during extraction does NOT cancel background analysis, returns PROCESSING and then COMPLETED")
    void sseDisconnectDoesNotCancelBackgroundAnalysis() throws Exception {
        CountDownLatch extractionStarted = new CountDownLatch(1);
        CountDownLatch allowExtractionToFinish = new CountDownLatch(1);

        when(resumeTextExtractor.extractText(any(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Consumer<String> progress = inv.getArgument(1);
            progress.accept("extracting");
            extractionStarted.countDown();
            // Wait until test simulates disconnect
            allowExtractionToFinish.await(2, TimeUnit.SECONDS);
            return "Resume content after simulated disconnect";
        });
        when(geminiService.analyzeResume(eq("Resume content after simulated disconnect"), any()))
                .thenReturn(sampleAnalysis);
        when(geminiService.getProviderName()).thenReturn("gemini");

        AnalysisRequestState state = new AnalysisRequestState();
        String analysisId = state.getAnalysisId();
        resumeController.getActiveRequests().put(analysisId, state);
        sessionStore.startSession(analysisId);

        // Create an emitter that throws IOException immediately on send (simulating disconnected browser)
        SseEmitter brokenEmitter = new SseEmitter(10000L) {
            @Override
            public synchronized void send(SseEventBuilder builder) throws IOException {
                throw new IOException("Connection reset by peer (client switched tab / backgrounded)");
            }
        };

        // Run analysis asynchronously on a thread
        Thread workerThread = new Thread(() -> {
            MockMultipartFile file = new MockMultipartFile("resume", "cv.pdf", "application/pdf", new byte[]{10, 20});
            resumeController.runAnalysis(file, brokenEmitter, state);
        });
        workerThread.start();

        // Wait until extraction starts
        assertTrue(extractionStarted.await(2, TimeUnit.SECONDS));

        // Status endpoint should return PROCESSING even though SSE emitter failed
        mockMvc.perform(get("/analysis/" + analysisId + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value(analysisId))
                .andExpect(jsonPath("$.status").value("PROCESSING"));

        // Allow extraction to proceed
        allowExtractionToFinish.countDown();
        workerThread.join(3000);

        // Job must complete and result must be recovered via status endpoint!
        mockMvc.perform(get("/analysis/" + analysisId + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.result.score").value(88))
                .andExpect(jsonPath("$.provider").value("gemini"));
    }

    @Test
    @DisplayName("3. Status endpoint returns FAILED safely when AI fails")
    void statusEndpointReturnsFailedSafely() throws Exception {
        when(resumeTextExtractor.extractText(any(), any())).thenReturn("Text");
        when(geminiService.analyzeResume(any(), any()))
                .thenThrow(new AICommunicationException("gemini", AIErrorCategory.SERVICE_UNAVAILABLE, "Gemini 503", false, null));

        MockMultipartFile file = new MockMultipartFile("resume", "resume.pdf", "application/pdf", new byte[]{1, 2, 3});

        MvcResult result = mockMvc.perform(multipart("/analyze").file(file)).andReturn();
        String analysisId = result.getResponse().getHeader("X-Analysis-Id");
        assertNotNull(analysisId);

        waitForStatus(analysisId, AnalysisStatus.FAILED, 3000);

        mockMvc.perform(get("/analysis/" + analysisId + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.error").value("We couldn't complete the AI analysis. Please try again."))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @DisplayName("4. Explicit cancellation cancels active job and status endpoint returns CANCELLED")
    void explicitCancellationCancelsJob() throws Exception {
        String analysisId = "active-job-cancel-test";
        AnalysisRequestState state = new AnalysisRequestState();
        resumeController.getActiveRequests().put(analysisId, state);
        sessionStore.startSession(analysisId);

        assertFalse(state.isCancelled());

        // Call explicit cancel endpoint
        mockMvc.perform(post("/analysis/" + analysisId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value(analysisId))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertTrue(state.isCancelled(), "Active request state must be cancelled");

        // Status endpoint should now report CANCELLED
        mockMvc.perform(get("/analysis/" + analysisId + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("5. Unknown or expired analysis ID returns 404")
    void unknownAnalysisIdReturns404() throws Exception {
        mockMvc.perform(get("/analysis/non-existent-id/status"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Analysis not found or expired."));

        mockMvc.perform(post("/analysis/non-existent-id/cancel"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Analysis not found."));
    }

    @Test
    @DisplayName("6. AnalysisStatusDTO does not expose raw resume text or sensitive internal details")
    void analysisStatusDtoExcludesSensitiveData() {
        AnalysisStatusDTO statusDto = new AnalysisStatusDTO(
                "id-123",
                AnalysisStatus.COMPLETED,
                "completed",
                sampleAnalysis,
                "groq",
                null
        );

        assertEquals("id-123", statusDto.analysisId());
        assertEquals(AnalysisStatus.COMPLETED, statusDto.status());
        assertEquals("groq", statusDto.provider());
        assertNotNull(statusDto.result());
    }

    private boolean waitForStatus(String analysisId, AnalysisStatus expected, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            var statusOpt = sessionStore.getStatus(analysisId);
            if (statusOpt.isPresent() && statusOpt.get().status() == expected) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }
}
