package com.resumeanalyzer.resume_analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import static org.mockito.Mockito.when;

@SpringBootTest(properties = {"refinecv.analysis.timeout=500ms", "refinecv.analysis.rate-limit.max-requests=100"})
@AutoConfigureMockMvc
class ResumeAnalysisIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("resumeAnalysisExecutor")
    private ThreadPoolTaskExecutor executor;

    @MockitoBean
    private ResumeTextExtractor resumeTextExtractor;

    @MockitoBean
    private GeminiService geminiService;

    @MockitoBean
    private GroqAIProvider groqAIProvider;

    @BeforeEach
    void waitForExecutorIdle() throws InterruptedException {
        var pool = executor.getThreadPoolExecutor();
        long deadline = System.currentTimeMillis() + 3000;
        while ((pool.getActiveCount() > 0 || !pool.getQueue().isEmpty()) && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
    }

    @Test
    void analysisRunsOnManagedExecutorAndEmitsSuccessfulTerminalEvent() throws Exception {
        AtomicReference<String> workerName = new AtomicReference<>();
        stubFastExtraction(workerName);
        stubGroqSuccess();

        MvcResult started = mockMvc.perform(multipart("/analyze").file(upload()))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = mockMvc.perform(asyncDispatch(started))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(workerName.get().startsWith("resume-analysis-"));
        assertTrue(body.contains("event:result"));
        assertTrue(body.contains("\"score\":80"));
        verify(groqAIProvider).analyzeResume(eq("resume text"), any());
        verify(geminiService, org.mockito.Mockito.never()).analyzeResume(any(), any());
    }

    @Test
    void missingResourceKeepsNotFoundStatusAndSafeResponse() throws Exception {
        mockMvc.perform(get("/missing-resource"))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.content().string("The requested resource was not found."));
    }

    @Test
    void executorHasBoundedCapacityAndSaturatedRequestGetsSafeSseError() throws Exception {
        var pool = executor.getThreadPoolExecutor();
        assertEquals(2, executor.getCorePoolSize());
        assertEquals(4, executor.getMaxPoolSize());
        assertEquals(8, pool.getQueue().remainingCapacity());

        CountDownLatch workersStarted = new CountDownLatch(4);
        CountDownLatch releaseWorkers = new CountDownLatch(1);
        List<Future<?>> acceptedTasks = new ArrayList<>();
        boolean rejectedAtCapacity = false;
        try {
            for (int i = 0; i < 20; i++) {
                try {
                    acceptedTasks.add(executor.submit(() -> {
                        workersStarted.countDown();
                        try {
                            releaseWorkers.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }));
                } catch (RejectedExecutionException expected) {
                    rejectedAtCapacity = true;
                    break;
                }
            }

            assertTrue(workersStarted.await(5, TimeUnit.SECONDS));
            assertTrue(rejectedAtCapacity);
            assertEquals(4, pool.getActiveCount());
            assertEquals(0, pool.getQueue().remainingCapacity());

            MvcResult rejected = mockMvc.perform(multipart("/analyze").file(upload()))
                    .andExpect(request().asyncStarted())
                    .andReturn();
            String body = mockMvc.perform(asyncDispatch(rejected))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            assertTrue(body.contains("event:error"));
            assertTrue(body.contains("currently processing too many resumes"));
            verify(geminiService, never()).analyzeResume(anyString(), any());
        } finally {
            releaseWorkers.countDown();
            for (Future<?> task : acceptedTasks) {
                task.get(5, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void timeoutInterruptsRequestAndEmitsTerminalErrorWithoutCallingGemini() throws Exception {
        CountDownLatch extractionStarted = new CountDownLatch(1);
        CountDownLatch extractionInterrupted = new CountDownLatch(1);
        when(resumeTextExtractor.extractText(any(byte[].class), any())).thenAnswer(invocation -> {
            extractionStarted.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                extractionInterrupted.countDown();
                Thread.currentThread().interrupt();
                throw new CancellationException("Interrupted after analysis timeout.");
            }
            return "resume text";
        });

        MvcResult started = mockMvc.perform(multipart("/analyze").file(upload()))
                .andExpect(request().asyncStarted())
                .andReturn();

        assertTrue(extractionStarted.await(2, TimeUnit.SECONDS));
        String body = mockMvc.perform(asyncDispatch(started))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(body.contains("event:error"));
        assertTrue(body.contains("The analysis took too long to complete"));
        assertTrue(extractionInterrupted.await(2, TimeUnit.SECONDS));
        verify(geminiService, never()).analyzeResume(anyString(), any());
    }

    @Test
    void validationFailureEmitsSafeTerminalSseError() throws Exception {
        when(resumeTextExtractor.extractText(any(byte[].class), any()))
                .thenThrow(new ResumeValidationException(ResumeValidationException.Reason.INVALID_PDF,
                        new IllegalArgumentException("C:\\private\\resume.pdf")));

        String body = performUploadAndReadSse();

        assertSafeError(body, "Please upload a valid, readable PDF resume.");
        assertTrue(!body.contains("private") && !body.contains("IllegalArgumentException"));
    }

    @Test
    void providerCommunicationFailureDoesNotExposeProviderDetails() throws Exception {
        stubFastExtraction(new AtomicReference<>());
        when(groqAIProvider.analyzeResume(eq("resume text"), any()))
                .thenThrow(new AICommunicationException("groq", AIErrorCategory.NETWORK_COMMUNICATION, "secret-api-key provider body", false, null));

        String body = performUploadAndReadSse();

        assertSafeError(body, "We couldn't complete the AI analysis. Please try again.");
        assertTrue(!body.contains("secret-api-key") && !body.contains("AICommunicationException"));
    }

    @Test
    void invalidGeminiResponseDoesNotExposeRawResponse() throws Exception {
        stubFastExtraction(new AtomicReference<>());
        when(groqAIProvider.analyzeResume(eq("resume text"), any()))
                .thenThrow(new AICommunicationException("groq", AIErrorCategory.RATE_QUOTA_EXHAUSTED, "Groq 429", true, null));
        when(geminiService.isAvailable()).thenReturn(true);
        when(geminiService.analyzeResume(eq("resume text"), any()))
                .thenThrow(new GeminiResponseException(GeminiResponseException.Reason.MALFORMED_JSON,
                        "RAW_GEMINI_RESPONSE {private-content}"));

        String body = performUploadAndReadSse();

        assertSafeError(body, "We couldn't validate the AI analysis. Please try again.");
        assertTrue(!body.contains("RAW_GEMINI_RESPONSE") && !body.contains("private-content"));
    }

    @Test
    void groqPrimaryRateLimitFallsBackToGeminiAndSucceeds() throws Exception {
        stubFastExtraction(new AtomicReference<>());
        when(groqAIProvider.analyzeResume(eq("resume text"), any()))
                .thenThrow(new AICommunicationException("groq", AIErrorCategory.RATE_QUOTA_EXHAUSTED,
                        "Groq rate limit exceeded (429)", true, null));
        when(geminiService.isAvailable()).thenReturn(true);
        stubGeminiSuccess();

        String body = performUploadAndReadSse();

        assertTrue(body.contains("ai-fallback"));
        assertTrue(body.contains("event:result"));
        assertTrue(body.contains("\"score\":80"));
        verify(groqAIProvider).analyzeResume(eq("resume text"), any());
        verify(geminiService).analyzeResume(eq("resume text"), any());
    }

    @Test
    void unexpectedFailureUsesGenericMessageWithoutStackOrExceptionDetails() throws Exception {
        when(resumeTextExtractor.extractText(any(byte[].class), any()))
                .thenThrow(new IllegalStateException("C:\\secret\\raw resume contents"));

        String body = performUploadAndReadSse();

        assertSafeError(body, "Something went wrong while analyzing the resume.");
        assertTrue(!body.contains("IllegalStateException") && !body.contains("secret")
                && !body.contains("at com.resumeanalyzer"));
    }

    private String performUploadAndReadSse() throws Exception {
        MvcResult started = mockMvc.perform(multipart("/analyze").file(upload()))
                .andExpect(request().asyncStarted())
                .andReturn();
        return mockMvc.perform(asyncDispatch(started)).andReturn().getResponse().getContentAsString();
    }

    private void assertSafeError(String body, String expectedMessage) {
        assertTrue(body.contains("event:error"));
        assertTrue(body.contains(expectedMessage));
        assertTrue(!body.contains("event:result"));
        assertTrue(!body.contains(" at "));
    }

    private void stubFastExtraction(AtomicReference<String> workerName) throws Exception {
        when(resumeTextExtractor.extractText(any(byte[].class), any())).thenAnswer(invocation -> {
            workerName.set(Thread.currentThread().getName());
            @SuppressWarnings("unchecked")
            Consumer<String> progress = invocation.getArgument(1);
            progress.accept("extracting");
            progress.accept("extracted");
            return "resume text";
        });
    }

    private void stubGroqSuccess() {
        when(groqAIProvider.analyzeResume(eq("resume text"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<String> progress = invocation.getArgument(1);
            progress.accept("ai-analysis");
            progress.accept("recommendations");
            return new ResumeAnalysisDTO(80, "Strong resume", List.of("Java"), List.of(),
                    List.of("Clear experience"), List.of(), "Good", List.of("Add metrics"),
                    List.of("Quantify impact"));
        });
    }

    private void stubGeminiSuccess() {
        when(geminiService.analyzeResume(eq("resume text"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<String> progress = invocation.getArgument(1);
            progress.accept("ai-analysis");
            progress.accept("recommendations");
            return new ResumeAnalysisDTO(80, "Strong resume", List.of("Java"), List.of(),
                    List.of("Clear experience"), List.of(), "Good", List.of("Add metrics"),
                    List.of("Quantify impact"));
        });
    }

    private MockMultipartFile upload() {
        return new MockMultipartFile("resume", "resume.pdf", "application/pdf", new byte[]{1});
    }
}
