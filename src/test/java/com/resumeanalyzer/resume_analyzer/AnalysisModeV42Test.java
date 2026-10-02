package com.resumeanalyzer.resume_analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.unit.DataSize;

/**
 * V4.2 focused tests: AnalysisMode parsing, server-side JD validation,
 * FallbackAIService job routing, and session store JD persistence.
 */
class AnalysisModeV42Test {

    /* A. AnalysisMode.fromString() */

    @Nested
    @DisplayName("A. AnalysisMode.fromString()")
    class AnalysisModeParsingTest {

        @Test
        @DisplayName("null or blank defaults to GENERAL")
        void nullOrBlankDefaultsToGeneral() {
            assertEquals(AnalysisMode.GENERAL, AnalysisMode.fromString(null));
            assertEquals(AnalysisMode.GENERAL, AnalysisMode.fromString(""));
            assertEquals(AnalysisMode.GENERAL, AnalysisMode.fromString("   "));
        }

        @Test
        @DisplayName("GENERAL parses correctly case-insensitive")
        void parsesGeneral() {
            assertEquals(AnalysisMode.GENERAL, AnalysisMode.fromString("GENERAL"));
            assertEquals(AnalysisMode.GENERAL, AnalysisMode.fromString("general"));
            assertEquals(AnalysisMode.GENERAL, AnalysisMode.fromString("General"));
        }

        @Test
        @DisplayName("SPECIFIC_JOB parses correctly case-insensitive")
        void parsesSpecificJob() {
            assertEquals(AnalysisMode.SPECIFIC_JOB, AnalysisMode.fromString("SPECIFIC_JOB"));
            assertEquals(AnalysisMode.SPECIFIC_JOB, AnalysisMode.fromString("specific_job"));
        }

        @Test
        @DisplayName("Invalid mode throws ResumeValidationException with INVALID_MODE reason")
        void invalidModeThrows() {
            ResumeValidationException ex = assertThrows(
                    ResumeValidationException.class,
                    () -> AnalysisMode.fromString("INVALID_MODE")
            );
            assertEquals(ResumeValidationException.Reason.INVALID_MODE, ex.getReason());
        }

        @Test
        @DisplayName("Invalid mode classifies as INVALID_MODE category")
        void invalidModeClassified() {
            ResumeValidationException ex = assertThrows(
                    ResumeValidationException.class,
                    () -> AnalysisMode.fromString("HACK_ATTEMPT")
            );
            assertEquals(AnalysisErrorMessages.Category.INVALID_MODE,
                    AnalysisErrorMessages.classify(ex));
        }

        @Test
        @DisplayName("INVALID_MODE category has a non-empty user-facing message")
        void invalidModeHasMessage() {
            String msg = AnalysisErrorMessages.forCategory(AnalysisErrorMessages.Category.INVALID_MODE);
            assertNotNull(msg);
            assertNotEquals("", msg.trim());
        }
    }

    /* B. validateModeAndJobDescription */

    @Nested
    @DisplayName("B. Controller JD validation")
    class JdValidationTest {

        private ResumeController controller;

        @BeforeEach
        void setUp() {
            controller = new ResumeController(
                    null,
                    (AIProvider) null,
                    DataSize.ofMegabytes(5),
                    new ThreadPoolTaskExecutor(),
                    new ThreadPoolTaskScheduler(),
                    Duration.ofMinutes(3),
                    Duration.ofSeconds(15),
                    new InMemoryAnalysisRateLimiter(5, Duration.ofMinutes(15), 100)
            );
        }

        @Test @DisplayName("GENERAL null JD passes")
        void generalNullJdPasses() {
            assertDoesNotThrow(() -> controller.validateModeAndJobDescription(AnalysisMode.GENERAL, null));
        }

        @Test @DisplayName("GENERAL blank JD passes")
        void generalBlankJdPasses() {
            assertDoesNotThrow(() -> controller.validateModeAndJobDescription(AnalysisMode.GENERAL, "  "));
        }

        @Test @DisplayName("GENERAL with JD passes")
        void generalWithJdPasses() {
            assertDoesNotThrow(() -> controller.validateModeAndJobDescription(AnalysisMode.GENERAL, "Some JD text"));
        }

        @Test @DisplayName("SPECIFIC_JOB with valid JD passes")
        void specificJobValidJdPasses() {
            assertDoesNotThrow(() -> controller.validateModeAndJobDescription(
                    AnalysisMode.SPECIFIC_JOB, "Software Engineer, Java required"));
        }

        @Test @DisplayName("SPECIFIC_JOB null JD rejected as MISSING_JOB_DESCRIPTION")
        void specificJobNullJdRejected() {
            ResumeValidationException ex = assertThrows(ResumeValidationException.class,
                    () -> controller.validateModeAndJobDescription(AnalysisMode.SPECIFIC_JOB, null));
            assertEquals(ResumeValidationException.Reason.MISSING_JOB_DESCRIPTION, ex.getReason());
        }

        @Test @DisplayName("SPECIFIC_JOB empty JD rejected as MISSING_JOB_DESCRIPTION")
        void specificJobEmptyJdRejected() {
            ResumeValidationException ex = assertThrows(ResumeValidationException.class,
                    () -> controller.validateModeAndJobDescription(AnalysisMode.SPECIFIC_JOB, ""));
            assertEquals(ResumeValidationException.Reason.MISSING_JOB_DESCRIPTION, ex.getReason());
        }

        @Test @DisplayName("SPECIFIC_JOB whitespace-only JD rejected as MISSING_JOB_DESCRIPTION")
        void specificJobWhitespaceJdRejected() {
            ResumeValidationException ex = assertThrows(ResumeValidationException.class,
                    () -> controller.validateModeAndJobDescription(AnalysisMode.SPECIFIC_JOB, "   \t\n"));
            assertEquals(ResumeValidationException.Reason.MISSING_JOB_DESCRIPTION, ex.getReason());
        }

        @Test @DisplayName("JD exactly 8000 chars is accepted for SPECIFIC_JOB")
        void jdAtMaxLengthAccepted() {
            String base = "Senior Software Engineer with Java and Spring Boot experience required. Qualifications: Degree in Computer Science and 3+ years experience. Responsibilities include microservices. ";
            String jd = base.repeat(100).substring(0, ResumeController.JD_MAX_LENGTH);
            assertDoesNotThrow(() -> controller.validateModeAndJobDescription(AnalysisMode.SPECIFIC_JOB, jd));
        }

        @Test @DisplayName("JD 8001 chars is rejected as JOB_DESCRIPTION_TOO_LONG")
        void jdOverMaxLengthRejected() {
            String base = "Senior Software Engineer with Java and Spring Boot experience required. Qualifications: Degree in Computer Science and 3+ years experience. Responsibilities include microservices. ";
            String jd = base.repeat(100).substring(0, ResumeController.JD_MAX_LENGTH + 1);
            ResumeValidationException ex = assertThrows(ResumeValidationException.class,
                    () -> controller.validateModeAndJobDescription(AnalysisMode.SPECIFIC_JOB, jd));
            assertEquals(ResumeValidationException.Reason.JOB_DESCRIPTION_TOO_LONG, ex.getReason());
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "banking sector",
                "Frontend Developer",
                "software developer",
                "marketing manager",
                "hospital receptionist"
        })
        @DisplayName("Meaningful short inputs are accepted in SPECIFIC_JOB mode")
        void meaningfulShortInputsAccepted(String jd) {
            assertDoesNotThrow(() -> controller.validateModeAndJobDescription(AnalysisMode.SPECIFIC_JOB, jd));
        }

        @Test @DisplayName("SPECIFIC_JOB symbol spam rejected as INSUFFICIENT_JOB_DESCRIPTION")
        void specificJobSymbolSpamRejected() {
            ResumeValidationException ex = assertThrows(ResumeValidationException.class,
                    () -> controller.validateModeAndJobDescription(AnalysisMode.SPECIFIC_JOB, "!@#$%^&*()_+ !@#$%^&*()_+ !@#$%^&*()_+"));
            assertEquals(ResumeValidationException.Reason.INSUFFICIENT_JOB_DESCRIPTION, ex.getReason());
        }

        @Test @DisplayName("SPECIFIC_JOB number spam rejected as INSUFFICIENT_JOB_DESCRIPTION")
        void specificJobNumberSpamRejected() {
            ResumeValidationException ex = assertThrows(ResumeValidationException.class,
                    () -> controller.validateModeAndJobDescription(AnalysisMode.SPECIFIC_JOB, "12345 67890 12345 67890 12345 67890"));
            assertEquals(ResumeValidationException.Reason.INSUFFICIENT_JOB_DESCRIPTION, ex.getReason());
        }

        @Test @DisplayName("SPECIFIC_JOB repetitive junk rejected as INSUFFICIENT_JOB_DESCRIPTION")
        void specificJobRepetitiveJunkRejected() {
            ResumeValidationException ex = assertThrows(ResumeValidationException.class,
                    () -> controller.validateModeAndJobDescription(AnalysisMode.SPECIFIC_JOB, "test test test test test"));
            assertEquals(ResumeValidationException.Reason.INSUFFICIENT_JOB_DESCRIPTION, ex.getReason());
        }

        @Test @DisplayName("INSUFFICIENT_JOB_DESCRIPTION classifies as INVALID_JOB_DESCRIPTION with exact required message")
        void insufficientJdClassifiedWithMessage() {
            var ex = new ResumeValidationException(ResumeValidationException.Reason.INSUFFICIENT_JOB_DESCRIPTION);
            assertEquals(AnalysisErrorMessages.Category.INVALID_JOB_DESCRIPTION,
                    AnalysisErrorMessages.classify(ex));
            assertEquals("Please enter a meaningful job description with enough detail to analyze the match.",
                    AnalysisErrorMessages.forException(ex));
        }

        @Test @DisplayName("Invalid JD never reaches AI provider")
        void invalidJdNeverReachesAiProvider() {
            AIProvider mockAi = org.mockito.Mockito.mock(AIProvider.class);
            ResumeController c = new ResumeController(
                    null,
                    mockAi,
                    DataSize.ofMegabytes(5),
                    new ThreadPoolTaskExecutor(),
                    new ThreadPoolTaskScheduler(),
                    Duration.ofMinutes(3),
                    Duration.ofSeconds(15),
                    new InMemoryAnalysisRateLimiter(5, Duration.ofMinutes(15), 100)
            );
            MockMultipartFile pdf = new MockMultipartFile(
                    "resume", "test.pdf", "application/pdf", "%PDF-1.4 dummy".getBytes());
            MockHttpServletRequest req = new MockHttpServletRequest();

            c.analyzeResume(pdf, "SPECIFIC_JOB", "!@#$%^&*()_+ !@#$%^&*()_+ !@#$%^&*()_+", req, null);

            verify(mockAi, never()).analyzeResume(any(), any());
            verify(mockAi, never()).analyzeResumeForJob(any(), any(), any());
        }

        @Test @DisplayName("GENERAL with oversized JD is also rejected")
        void generalOversizedJdRejected() {
            String jd = "B".repeat(ResumeController.JD_MAX_LENGTH + 1);
            ResumeValidationException ex = assertThrows(ResumeValidationException.class,
                    () -> controller.validateModeAndJobDescription(AnalysisMode.GENERAL, jd));
            assertEquals(ResumeValidationException.Reason.JOB_DESCRIPTION_TOO_LONG, ex.getReason());
        }

        @Test @DisplayName("MISSING_JOB_DESCRIPTION classifies as INVALID_JOB_DESCRIPTION")
        void missingJdClassified() {
            var ex = new ResumeValidationException(ResumeValidationException.Reason.MISSING_JOB_DESCRIPTION);
            assertEquals(AnalysisErrorMessages.Category.INVALID_JOB_DESCRIPTION,
                    AnalysisErrorMessages.classify(ex));
        }

        @Test @DisplayName("INVALID_JOB_DESCRIPTION has non-empty user-facing message")
        void invalidJdHasMessage() {
            String msg = AnalysisErrorMessages.forCategory(AnalysisErrorMessages.Category.INVALID_JOB_DESCRIPTION);
            assertNotNull(msg);
            assertNotEquals("", msg.trim());
        }

        @Test @DisplayName("JD_MAX_LENGTH constant equals 8000")
        void jdMaxLengthIs8000() {
            assertEquals(8000, ResumeController.JD_MAX_LENGTH);
        }
    }

    /* C. FallbackAIService V4.2 routing */

    @Nested
    @DisplayName("C. FallbackAIService V4.2 routing")
    @ExtendWith(MockitoExtension.class)
    class FallbackRoutingV42Test {

        @Mock private AIProvider primaryProvider;
        @Mock private AIProvider fallbackProvider;
        private FallbackAIService fallbackService;

        private final ResumeAnalysisDTO sampleAnalysis = new ResumeAnalysisDTO(
                80, "Good resume.", List.of("Java"), List.of("Kubernetes"),
                List.of("Leadership"), List.of("No cloud"), "Good", List.of("Add metrics"), List.of());

        private final ResumeImprovementDTO sampleImprovement = new ResumeImprovementDTO(
                "Improved summary",
                List.of(new BulletImprovementDTO("Experience", "old", "new", "clarity")),
                List.of("Better framing"), List.of("Add metrics"));

        @BeforeEach
        void setUp() {
            fallbackService = new FallbackAIService(primaryProvider, fallbackProvider);
        }

        @Test @DisplayName("GENERAL mode calls analyzeResume, never analyzeResumeForJob")
        void generalModeCallsGeneralPath() {
            when(primaryProvider.analyzeResume(eq("resume"), any())).thenReturn(sampleAnalysis);
            when(primaryProvider.getProviderName()).thenReturn("gemini");
            fallbackService.analyzeResumeWithProvider("resume", AnalysisMode.GENERAL, null, s -> {});
            verify(primaryProvider).analyzeResume(eq("resume"), any());
            verify(primaryProvider, never()).analyzeResumeForJob(any(), any(), any());
        }

        @Test @DisplayName("SPECIFIC_JOB with JD calls analyzeResumeForJob")
        void specificJobCallsJobPath() {
            when(primaryProvider.analyzeResumeForJob(eq("resume"), eq("JD text"), any()))
                    .thenReturn(sampleAnalysis);
            when(primaryProvider.getProviderName()).thenReturn("gemini");
            fallbackService.analyzeResumeWithProvider("resume", AnalysisMode.SPECIFIC_JOB, "JD text", s -> {});
            verify(primaryProvider).analyzeResumeForJob(eq("resume"), eq("JD text"), any());
            verify(primaryProvider, never()).analyzeResume(any(), any());
        }

        @Test @DisplayName("SPECIFIC_JOB + null JD falls back to general path in FallbackAIService")
        void specificJobNullJdUsesGeneralPath() {
            when(primaryProvider.analyzeResume(eq("resume"), any())).thenReturn(sampleAnalysis);
            when(primaryProvider.getProviderName()).thenReturn("gemini");
            fallbackService.analyzeResumeWithProvider("resume", AnalysisMode.SPECIFIC_JOB, null, s -> {});
            verify(primaryProvider).analyzeResume(eq("resume"), any());
            verify(primaryProvider, never()).analyzeResumeForJob(any(), any(), any());
        }

        @Test @DisplayName("Job improvement calls improveResumeForJob with correct JD")
        void jobImprovementCallsJobPath() {
            when(primaryProvider.improveResumeForJob(eq("resume"), eq(sampleAnalysis), eq("JD")))
                    .thenReturn(sampleImprovement);
            when(primaryProvider.getProviderName()).thenReturn("gemini");
            fallbackService.improveResumeWithProvider("resume", sampleAnalysis, "JD");
            verify(primaryProvider).improveResumeForJob(eq("resume"), eq(sampleAnalysis), eq("JD"));
            verify(primaryProvider, never()).improveResume(any(), any());
        }

        @Test @DisplayName("General improvement (null JD) calls improveResume, never improveResumeForJob")
        void generalImprovementCallsGeneralPath() {
            when(primaryProvider.improveResume(eq("resume"), eq(sampleAnalysis))).thenReturn(sampleImprovement);
            when(primaryProvider.getProviderName()).thenReturn("gemini");
            fallbackService.improveResumeWithProvider("resume", sampleAnalysis, null);
            verify(primaryProvider).improveResume(eq("resume"), eq(sampleAnalysis));
            verify(primaryProvider, never()).improveResumeForJob(any(), any(), any());
        }
    }

    /* D. Session store JD persistence */

    @Nested
    @DisplayName("D. AnalysisSessionStore mode and JD persistence")
    class SessionStoreV42Test {

        private AnalysisSessionStore store;

        private final ResumeAnalysisDTO sampleAnalysis = new ResumeAnalysisDTO(
                75, "Good.", List.of("Java"), List.of(), List.of(), List.of(),
                "Medium", List.of(), List.of());

        @BeforeEach
        void setUp() {
            store = new AnalysisSessionStore(Duration.ofMinutes(30), 100);
        }

        @Test @DisplayName("SPECIFIC_JOB session preserves mode and JD")
        void specificJobPreservesModeAndJd() {
            store.startSession("sid-1");
            store.completeSession("sid-1", "text", sampleAnalysis, "gemini",
                    AnalysisMode.SPECIFIC_JOB, "Sr. Engineer at ACME");
            var s = store.get("sid-1");
            assert s.isPresent();
            assertEquals(AnalysisMode.SPECIFIC_JOB, s.get().mode());
            assertEquals("Sr. Engineer at ACME", s.get().jobDescription());
        }

        @Test @DisplayName("GENERAL session has null JD")
        void generalSessionNullJd() {
            store.startSession("sid-2");
            store.completeSession("sid-2", "text", sampleAnalysis, "gemini",
                    AnalysisMode.GENERAL, null);
            var s = store.get("sid-2");
            assert s.isPresent();
            assertEquals(AnalysisMode.GENERAL, s.get().mode());
            assertNull(s.get().jobDescription());
        }

        @Test @DisplayName("GENERAL session does not inherit JD from prior SPECIFIC_JOB session")
        void generalDoesNotInheritPriorJd() {
            store.startSession("job-sid");
            store.completeSession("job-sid", "text", sampleAnalysis, "gemini",
                    AnalysisMode.SPECIFIC_JOB, "DevOps Engineer JD");

            store.startSession("gen-sid");
            store.completeSession("gen-sid", "text2", sampleAnalysis, "gemini",
                    AnalysisMode.GENERAL, null);

            var gen = store.get("gen-sid");
            assert gen.isPresent();
            assertNull(gen.get().jobDescription());
        }

        @Test @DisplayName("FullSession exposes mode and JD via getters")
        void fullSessionGetters() {
            store.startSession("full-sid");
            store.completeSession("full-sid", "text", sampleAnalysis, "groq",
                    AnalysisMode.SPECIFIC_JOB, "Data Engineer");
            var full = store.getFullSession("full-sid");
            assert full.isPresent();
            assertEquals(AnalysisMode.SPECIFIC_JOB, full.get().getMode());
            assertEquals("Data Engineer", full.get().getJobDescription());
        }
    }
}
