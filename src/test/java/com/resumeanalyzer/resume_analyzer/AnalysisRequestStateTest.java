package com.resumeanalyzer.resume_analyzer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.FutureTask;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.unit.DataSize;

class AnalysisRequestStateTest {

    @Test
    void cancellationDuringExtractionPreventsGeminiCallAndCancelsOnlyRequestTask() throws Exception {
        ResumeTextExtractor extractor = org.mockito.Mockito.mock(ResumeTextExtractor.class);
        GeminiService gemini = org.mockito.Mockito.mock(GeminiService.class);
        ResumeController controller = new ResumeController(
                extractor,
                gemini,
                DataSize.ofMegabytes(5),
                new ThreadPoolTaskExecutor(),
                new ThreadPoolTaskScheduler(),
                Duration.ofMinutes(3),
                Duration.ofSeconds(15),
                new InMemoryAnalysisRateLimiter(5, Duration.ofMinutes(15), 100)
        );

        AnalysisRequestState state = new AnalysisRequestState();
        FutureTask<Void> task = new FutureTask<>(() -> null);
        state.setTask(task);

        when(extractor.extractText(any(byte[].class), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<String> progress = invocation.getArgument(1);
            progress.accept("extracting");
            state.cancel(); // Simulates the emitter reporting a client disconnect.
            return "resume text";
        });

        controller.runAnalysis(
                new MockMultipartFile("resume", "resume.pdf", "application/pdf", new byte[]{1}),
                new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(),
                state
        );

        assertTrue(task.isCancelled());
        assertThrows(java.util.concurrent.CancellationException.class, state::checkActive);
        verify(gemini, never()).analyzeResume(any(String.class), any());
    }
}
