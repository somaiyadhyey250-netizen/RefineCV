package com.resumeanalyzer.resume_analyzer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewPrepFeatureTest {

    @Mock
    private AIProvider primaryGemini;

    @Mock
    private AIProvider fallbackGroq;

    private String currentTimestamp() {
        return Instant.now().toString();
    }

    private String expiredTimestamp() {
        return Instant.now().minus(Duration.ofHours(2)).toString();
    }

    private List<InterviewQuestionDTO> createSampleQuestions(int startIdx, int endIdx) {
        List<InterviewQuestionDTO> list = new ArrayList<>();
        for (int i = startIdx; i <= endIdx; i++) {
            list.add(new InterviewQuestionDTO(
                    "q-" + i,
                    "How did you design the REST API for service " + i + "?",
                    i % 3 == 0 ? "WHY" : (i % 2 == 0 ? "PROOF" : "DEPTH"),
                    i % 3 == 0 ? "RISKY" : (i % 2 == 0 ? "BE_READY" : "SAFE"),
                    "Built a REST API using Spring Boot for service " + i,
                    "Verify actual architectural contribution.",
                    "Explain endpoints, validation, and testing."
            ));
        }
        return list;
    }

    private List<InterviewClaimDTO> createSampleClaims() {
        return List.of(
                new InterviewClaimDTO("c-1", "Led cross-functional migration to microservices", "RISKY", "Clarify exact team size and your direct role."),
                new InterviewClaimDTO("c-2", "Improved API throughput by 40%", "BE_READY", "Be prepared to explain baseline and profiling tools used.")
        );
    }

    @Nested
    @DisplayName("Domain Model & DTO Validation")
    class DomainModelValidationTests {

        @Test
        @DisplayName("Valid InterviewPrepDTO builds successfully")
        void validDtoBuilds() {
            var questions = createSampleQuestions(1, 7);
            var claims = createSampleClaims();
            var prep = new InterviewPrepDTO(
                    "prep-123",
                    "Resume.pdf",
                    currentTimestamp(),
                    7,
                    questions,
                    claims,
                    "Focus your prep on REST API architecture.",
                    false
            );

            assertEquals("prep-123", prep.id());
            assertEquals(7, prep.questionCount());
            assertEquals(7, prep.questions().size());
            assertEquals(2, prep.claimsToPrepare().size());
            assertFalse(prep.isUnreadable());
        }

        @Test
        @DisplayName("InterviewPrepDTO rejects invalid questions or count")
        void rejectsInvalidQuestionCount() {
            assertThrows(IllegalArgumentException.class, () -> new InterviewPrepDTO(
                    "prep-err",
                    "Resume.pdf",
                    currentTimestamp(),
                    0,
                    List.of(),
                    List.of(),
                    "Note",
                    false
            ));
        }

        @Test
        @DisplayName("withMergedQuestions preserves existing questions and appends new ones up to cap")
        void withMergedQuestionsPreservesAndAppends() {
            var initial = new InterviewPrepDTO(
                    "prep-merge",
                    "Resume.pdf",
                    currentTimestamp(),
                    7,
                    createSampleQuestions(1, 7),
                    createSampleClaims(),
                    "Note",
                    false
            );

            var additional = List.of(
                    new InterviewQuestionDTO("q-8", "Why did you choose PostgreSQL?", "WHY", "BE_READY", "Used PostgreSQL database", "Database choice", "Explain ACID guarantees"),
                    new InterviewQuestionDTO("q-9", "How did you manage database connection pooling?", "DEPTH", "SAFE", "PostgreSQL database", "Scalability", "Explain HikariCP settings")
            );

            var updated = initial.withMergedQuestions(additional);
            assertEquals(9, updated.questionCount());
            assertEquals(9, updated.questions().size());
            assertEquals("q-1", updated.questions().get(0).id());
            assertEquals("q-8", updated.questions().get(7).id());
        }

        @Test
        @DisplayName("forUnreadable factory creates valid error DTO")
        void forUnreadableFactory() {
            var unreadable = InterviewPrepDTO.forUnreadable("prep-unreadable", "Scanned.pdf", "Unreadable scan");
            assertTrue(unreadable.isUnreadable());
            assertEquals(0, unreadable.questionCount());
            assertTrue(unreadable.questions().isEmpty());
            assertEquals("Scanned.pdf", unreadable.filename());
        }
    }

    @Nested
    @DisplayName("AI Response Parsing Contract")
    class AIResponseParserTests {

        @Test
        @DisplayName("Valid JSON parses into InterviewPrepDTO")
        void parsesValidJson() {
            String json = """
            {
              "questions": [
                {
                  "id": "q-1",
                  "question": "How did you implement authentication in Spring Boot?",
                  "questionType": "DEPTH",
                  "riskLevel": "BE_READY",
                  "basedOn": "Implemented JWT authentication with Spring Security",
                  "interviewerIntent": "Check implementation details of security filter",
                  "preparationHint": "Describe filter chain and token validation"
                }
              ],
              "claimsToPrepare": [
                {
                  "id": "c-1",
                  "claim": "Implemented JWT authentication with Spring Security",
                  "riskLevel": "SAFE",
                  "preparationNote": "Explain token expiry and signing key storage"
                }
              ],
              "overallPreparationNote": "Solid technical foundation in Java security."
            }
            """;

            var prep = AIResponseParser.parseAndValidateInterviewPrep(json, "prep-parse", "Resume.pdf");
            assertNotNull(prep);
            assertEquals(1, prep.questionCount());
            assertEquals("DEPTH", prep.questions().get(0).questionType());
            assertEquals("BE_READY", prep.questions().get(0).riskLevel());
            assertEquals(1, prep.claimsToPrepare().size());
            assertEquals("Solid technical foundation in Java security.", prep.overallPreparationNote());
        }

        @Test
        @DisplayName("Malformed JSON throws GeminiResponseException")
        void malformedJsonThrows() {
            String badJson = "{ invalid json content ...";
            assertThrows(GeminiResponseException.class, () ->
                    AIResponseParser.parseAndValidateInterviewPrep(badJson, "prep-bad", "Resume.pdf"));
        }

        @Test
        @DisplayName("JSON without questions throws GeminiResponseException")
        void missingQuestionsThrows() {
            String json = """
            {
              "claimsToPrepare": [],
              "overallPreparationNote": "Missing questions"
            }
            """;
            assertThrows(GeminiResponseException.class, () ->
                    AIResponseParser.parseAndValidateInterviewPrep(json, "prep-bad", "Resume.pdf"));
        }

        @Test
        @DisplayName("Answer evaluation JSON parses correctly")
        void answerEvaluationParsing() {
            String json = """
            {
              "answerQuality": "The response clearly addresses the core architectural concepts.",
              "strengths": [
                "Clearly explains token generation and validation",
                "Mentions symmetric encryption keys"
              ],
              "improvements": [
                "Mention handling of token expiration or refresh tokens"
              ]
            }
            """;

            var eval = AIResponseParser.parseAndValidateAnswerEvaluation(json, "q-1");
            assertEquals("q-1", eval.questionId());
            assertEquals(2, eval.strengths().size());
            assertEquals(1, eval.improvements().size());
            assertTrue(eval.answerQuality().contains("architectural concepts"));
        }
    }

    @Nested
    @DisplayName("Prompt Builder Security & Grounding")
    class PromptBuilderTests {

        @Test
        @DisplayName("Interview prep prompt includes anti-fabrication and boundary delimiter")
        void prepPromptAntiFabrication() {
            String resumeText = "Software Engineer with 3 years experience building Java APIs.";
            String prompt = AIPromptBuilder.buildInterviewPrepPrompt(resumeText);

            assertTrue(prompt.contains("STRICT GROUNDING: Every question and claim MUST be derived directly"));
            assertTrue(prompt.contains("NO FABRICATION: Never invent skills"));
            assertTrue(prompt.contains("Software Engineer with 3 years experience"));
            assertTrue(prompt.contains("DEPTH"));
            assertTrue(prompt.contains("PROOF"));
            assertTrue(prompt.contains("WHY"));
        }

        @Test
        @DisplayName("More questions prompt includes existing questions to prevent duplicates")
        void moreQuestionsExcludesDuplicates() {
            String resumeText = "Built a microservices pipeline with Kafka and Docker.";
            List<String> existing = List.of(
                    "How did you configure Kafka consumers?",
                    "Why did you choose Docker for containerization?"
            );

            String prompt = AIPromptBuilder.buildGenerateMoreQuestionsPrompt(resumeText, existing);
            assertTrue(prompt.contains("How did you configure Kafka consumers?"));
            assertTrue(prompt.contains("Why did you choose Docker for containerization?"));
            assertTrue(prompt.contains("DO NOT duplicate or rephrase"));
        }
    }

    @Nested
    @DisplayName("Fallback AI Service (Gemini -> Groq)")
    class FallbackAIServiceTests {

        private FallbackAIService fallbackService;

        @BeforeEach
        void setup() {
            fallbackService = new FallbackAIService(primaryGemini, fallbackGroq);
        }

        @Test
        @DisplayName("Gemini success returns Gemini result without calling Groq")
        void geminiSuccess() {
            var prep = new InterviewPrepDTO("prep-g", "Resume.pdf", currentTimestamp(), 7,
                    createSampleQuestions(1, 7), createSampleClaims(), "Note", false);
            when(primaryGemini.generateInterviewPrep(eq("resume text"), eq("prep-g"), eq("Resume.pdf"), any()))
                    .thenReturn(prep);
            when(primaryGemini.getProviderName()).thenReturn("gemini-2.5-flash");

            var result = fallbackService.generateInterviewPrepWithProvider("resume text", "prep-g", "Resume.pdf", null);
            assertEquals("gemini-2.5-flash", result.providerName());
            assertEquals(7, result.prep().questionCount());
            verify(fallbackGroq, never()).generateInterviewPrep(anyString(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("Gemini rate limit (429) triggers Groq fallback")
        void geminiRateLimitFallsBackToGroq() {
            var prep = new InterviewPrepDTO("prep-fallback", "Resume.pdf", currentTimestamp(), 7,
                    createSampleQuestions(1, 7), createSampleClaims(), "Note", false);
            when(primaryGemini.generateInterviewPrep(eq("resume text"), eq("prep-fallback"), eq("Resume.pdf"), any()))
                    .thenThrow(new AICommunicationException("gemini", AIErrorCategory.RATE_QUOTA_EXHAUSTED, "Gemini rate limit", true, null));
            when(fallbackGroq.isAvailable()).thenReturn(true);
            when(fallbackGroq.generateInterviewPrep(eq("resume text"), eq("prep-fallback"), eq("Resume.pdf"), any()))
                    .thenReturn(prep);
            when(fallbackGroq.getProviderName()).thenReturn("groq-gpt-oss-20b");

            var result = fallbackService.generateInterviewPrepWithProvider("resume text", "prep-fallback", "Resume.pdf", null);
            assertEquals("groq-gpt-oss-20b", result.providerName());
            assertEquals(7, result.prep().questionCount());
            verify(fallbackGroq).generateInterviewPrep(anyString(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("Gemini timeout triggers Groq fallback")
        void geminiTimeoutFallsBackToGroq() {
            var prep = new InterviewPrepDTO("prep-timeout", "Resume.pdf", currentTimestamp(), 7,
                    createSampleQuestions(1, 7), createSampleClaims(), "Note", false);
            when(primaryGemini.generateInterviewPrep(eq("resume text"), eq("prep-timeout"), eq("Resume.pdf"), any()))
                    .thenThrow(new AICommunicationException("gemini", AIErrorCategory.TIMEOUT, "Connection timeout", true, null));
            when(fallbackGroq.isAvailable()).thenReturn(true);
            when(fallbackGroq.generateInterviewPrep(eq("resume text"), eq("prep-timeout"), eq("Resume.pdf"), any()))
                    .thenReturn(prep);
            when(fallbackGroq.getProviderName()).thenReturn("groq-gpt-oss-20b");

            var result = fallbackService.generateInterviewPrepWithProvider("resume text", "prep-timeout", "Resume.pdf", null);
            assertEquals("groq-gpt-oss-20b", result.providerName());
            verify(fallbackGroq).generateInterviewPrep(anyString(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("When both primary and fallback fail, exception is propagated")
        void bothFailThrowsException() {
            when(primaryGemini.generateInterviewPrep(eq("resume text"), eq("prep-both-fail"), eq("Resume.pdf"), any()))
                    .thenThrow(new AICommunicationException("gemini", AIErrorCategory.TIMEOUT, "Primary fail", true, null));
            when(fallbackGroq.isAvailable()).thenReturn(true);
            when(fallbackGroq.generateInterviewPrep(eq("resume text"), eq("prep-both-fail"), eq("Resume.pdf"), any()))
                    .thenThrow(new AICommunicationException("groq", AIErrorCategory.TIMEOUT, "Fallback fail", true, null));

            assertThrows(AICommunicationException.class, () ->
                    fallbackService.generateInterviewPrepWithProvider("resume text", "prep-both-fail", "Resume.pdf", null));
        }
    }

    @Nested
    @DisplayName("AnalysisSessionStore Persistence & History Integration")
    class SessionStoreHistoryTests {

        private AnalysisSessionStore sessionStore;

        @BeforeEach
        void setup() {
            sessionStore = new AnalysisSessionStore(Duration.ofMinutes(30), 100);
        }

        @Test
        @DisplayName("Saves and retrieves InterviewPrepDTO without calling AI")
        void saveAndRetrievePrep() {
            var prep = new InterviewPrepDTO("prep-store-1", "Resume.pdf", currentTimestamp(), 7,
                    createSampleQuestions(1, 7), createSampleClaims(), "Preparation Note", false);

            sessionStore.saveInterviewPrep(prep);
            var opt = sessionStore.getInterviewPrep("prep-store-1");
            assertTrue(opt.isPresent());
            assertEquals(7, opt.get().questionCount());
            assertEquals("Resume.pdf", opt.get().filename());
        }

        @Test
        @DisplayName("Interview prep appears in recent completed sessions with INTERVIEW_PREP type")
        void prepAppearsInHistory() {
            var prep = new InterviewPrepDTO("prep-hist-1", "Backend_Dev.pdf", currentTimestamp(), 7,
                    createSampleQuestions(1, 7), createSampleClaims(), "Focus on concurrency", false);
            sessionStore.saveInterviewPrep(prep);

            var history = sessionStore.getRecentCompletedSessions();
            assertEquals(1, history.size());
            assertEquals("prep-hist-1", history.get(0).analysisId());
            assertEquals("INTERVIEW_PREP", history.get(0).type());
            assertEquals("Backend_Dev.pdf", history.get(0).fileName());
            assertEquals(7, history.get(0).score());
        }

        @Test
        @DisplayName("Clear history removes interview preps while preserving active processing session")
        void clearHistoryRemovesPreps() {
            var prep = new InterviewPrepDTO("prep-clear", "Dev.pdf", currentTimestamp(), 7,
                    createSampleQuestions(1, 7), createSampleClaims(), "Note", false);
            sessionStore.saveInterviewPrep(prep);

            sessionStore.startSession("active-1"); // active session

            sessionStore.clearHistory();

            assertTrue(sessionStore.getInterviewPrep("prep-clear").isEmpty());
            assertTrue(sessionStore.getRecentCompletedSessions().isEmpty());
            assertTrue(sessionStore.getStatus("active-1").isPresent()); // active preserved!
        }

        @Test
        @DisplayName("Expired interview prep session is pruned by cleanExpired")
        void expiredPrepIsCleanedUp() {
            var expiredPrep = new InterviewPrepDTO("prep-old", "Old.pdf", expiredTimestamp(), 7,
                    createSampleQuestions(1, 7), createSampleClaims(), "Old Note", false);
            sessionStore.saveInterviewPrep(expiredPrep);
            assertTrue(sessionStore.getInterviewPrep("prep-old").isEmpty());
        }
    }

    @Nested
    @DisplayName("Generate More Questions & Answer Evaluation Endpoints")
    class EndpointLogicTests {

        private InterviewPrepController controller;
        private AnalysisSessionStore sessionStore;
        private InMemoryAnalysisRateLimiter rateLimiter;

        @BeforeEach
        void setup() {
            sessionStore = new AnalysisSessionStore(Duration.ofMinutes(30), 100);
            rateLimiter = new InMemoryAnalysisRateLimiter(100, Duration.ofMinutes(1), 1000);
            controller = new InterviewPrepController(
                    null,
                    primaryGemini,
                    org.springframework.util.unit.DataSize.ofMegabytes(5),
                    null,
                    null,
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(5),
                    rateLimiter,
                    sessionStore
            );
        }

        @Test
        @DisplayName("Generate more questions appends new questions and enforces 15 question cap")
        void generateMoreQuestionsEnforcesCap() {
            var prep = new InterviewPrepDTO("prep-more", "Resume.pdf", currentTimestamp(), 12,
                    createSampleQuestions(1, 12), createSampleClaims(), "Note", false);
            sessionStore.saveInterviewPrep(prep);
            sessionStore.savePrepResumeText("prep-more", "Sample resume text containing projects and skills");

            var newQuestions = createSampleQuestions(13, 17); // 12 + 5 = 17, but should cap at 15
            when(primaryGemini.generateMoreQuestions(anyString(), anyList()))
                    .thenReturn(newQuestions);

            var req = new MockHttpServletRequest();
            ResponseEntity<?> response = controller.generateMoreQuestions("prep-more", req);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            @SuppressWarnings("unchecked")
            var body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertEquals(true, body.get("success"));
            assertEquals(15, body.get("totalQuestions")); // capped at 15!
            assertEquals(true, body.get("capped"));
        }

        @Test
        @DisplayName("Generate more questions returns 400 when already at 15 question limit")
        void generateMoreWhenAlreadyCapped() {
            var prep = new InterviewPrepDTO("prep-capped", "Resume.pdf", currentTimestamp(), 15,
                    createSampleQuestions(1, 15), createSampleClaims(), "Note", false);
            sessionStore.saveInterviewPrep(prep);
            sessionStore.savePrepResumeText("prep-capped", "Sample resume text");

            var req = new MockHttpServletRequest();
            ResponseEntity<?> response = controller.generateMoreQuestions("prep-capped", req);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            verify(primaryGemini, never()).generateMoreQuestions(anyString(), anyList());
        }

        @Test
        @DisplayName("Evaluate answer validates input and returns answer quality")
        void evaluateAnswerValidatesAndReturns() {
            var evalResult = new AIAnswerEvaluationResult(
                    new InterviewAnswerEvaluationDTO("q-1", "Strong, concise explanation.", List.of("Addresses architecture"), List.of("Add metrics")),
                    "gemini-2.5-flash"
            );

            when(primaryGemini.evaluateAnswerWithProvider(anyString(), anyString(), anyString()))
                    .thenReturn(evalResult);

            var req = new MockHttpServletRequest();
            var payload = new InterviewPrepController.PracticeAnswerRequest(
                    "q-1",
                    "How did you implement the REST API?",
                    "Built REST API in Spring Boot",
                    "I used Spring Boot with Spring Web and configured @RestController endpoints."
            );

            ResponseEntity<?> response = controller.evaluateAnswer("prep-eval", payload, req);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            var eval = (InterviewAnswerEvaluationDTO) response.getBody();
            assertNotNull(eval);
            assertEquals("Strong, concise explanation.", eval.answerQuality());
        }

        @Test
        @DisplayName("Evaluate answer rejects empty userAnswer")
        void evaluateAnswerRejectsEmpty() {
            var req = new MockHttpServletRequest();
            var payload = new InterviewPrepController.PracticeAnswerRequest(
                    "q-1",
                    "How did you implement the REST API?",
                    "Built REST API",
                    "   "
            );

            ResponseEntity<?> response = controller.evaluateAnswer("prep-eval", payload, req);
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }
    }
}
