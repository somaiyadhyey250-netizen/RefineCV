package com.resumeanalyzer.resume_analyzer;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompareResumesFeatureTest {

    @Mock
    private AIProvider primaryProvider;

    @Mock
    private AIProvider fallbackProvider;

    private List<CategoryComparisonDTO> createSampleCategories(int scoreA, int scoreB) {
        return List.of(
                new CategoryComparisonDTO("Content & Relevance", 20, 16, 18, "B", "Ev A1", "Ev B1", "Exp A1", "Exp B1"),
                new CategoryComparisonDTO("Skills & Keywords", 20, 17, 18, "B", "Ev A2", "Ev B2", "Exp A2", "Exp B2"),
                new CategoryComparisonDTO("Experience & Evidence", 20, 15, 17, "B", "Ev A3", "Ev B3", "Exp A3", "Exp B3"),
                new CategoryComparisonDTO("Impact & Achievements", 15, 10, 13, "B", "Ev A4", "Ev B4", "Exp A4", "Exp B4"),
                new CategoryComparisonDTO("Clarity & Structure", 15, 12, 12, "TIE", "Ev A5", "Ev B5", "Exp A5", "Exp B5"),
                new CategoryComparisonDTO("ATS Compatibility", 10, 9, 8, "A", "Ev A6", "Ev B6", "Exp A6", "Exp B6")
        );
    }

    @Nested
    @DisplayName("Deterministic Verdict and Threshold Logic")
    class VerdictThresholdTests {

        @Test
        @DisplayName("Difference >= 8 results in '[Winner] is stronger overall'")
        void differenceEightOrMoreStrongerOverall() {
            var categories = List.of(
                    new CategoryComparisonDTO("Content & Relevance", 20, 18, 12, "A", "Ev A", "Ev B", "Exp A", "Exp B"),
                    new CategoryComparisonDTO("Skills & Keywords", 20, 18, 12, "A", "Ev A", "Ev B", "Exp A", "Exp B"),
                    new CategoryComparisonDTO("Experience & Evidence", 20, 18, 12, "A", "Ev A", "Ev B", "Exp A", "Exp B"),
                    new CategoryComparisonDTO("Impact & Achievements", 15, 12, 10, "A", "Ev A", "Ev B", "Exp A", "Exp B"),
                    new CategoryComparisonDTO("Clarity & Structure", 15, 12, 12, "TIE", "Ev A", "Ev B", "Exp A", "Exp B"),
                    new CategoryComparisonDTO("ATS Compatibility", 10, 8, 8, "TIE", "Ev A", "Ev B", "Exp A", "Exp B")
            ); // A = 86, B = 66, diff = 20

            var comparison = ResumeComparisonDTO.of(
                    "cmp-1", "A.pdf", "B.pdf", null,
                    categories,
                    List.of("Diff 1", "Diff 2", "Diff 3"),
                    "Takeaway",
                    List.of("Borrow A"),
                    List.of("Borrow B")
            );

            assertEquals(86, comparison.totalScoreA());
            assertEquals(66, comparison.totalScoreB());
            assertEquals(20, comparison.scoreDifference());
            assertEquals("A", comparison.winner());
            assertEquals("Resume A is stronger overall", comparison.winnerVerdict());
            assertEquals(3, comparison.winnerDifferentiators().size());
        }

        @Test
        @DisplayName("Difference 4-7 results in '[Winner] has a narrow overall advantage'")
        void differenceFourToSevenNarrowAdvantage() {
            var categories = List.of(
                    new CategoryComparisonDTO("Content & Relevance", 20, 15, 16, "B", "Ev A", "Ev B", "Exp A", "Exp B"),
                    new CategoryComparisonDTO("Skills & Keywords", 20, 16, 17, "B", "Ev A", "Ev B", "Exp A", "Exp B"),
                    new CategoryComparisonDTO("Experience & Evidence", 20, 15, 17, "B", "Ev A", "Ev B", "Exp A", "Exp B"),
                    new CategoryComparisonDTO("Impact & Achievements", 15, 11, 12, "B", "Ev A", "Ev B", "Exp A", "Exp B"),
                    new CategoryComparisonDTO("Clarity & Structure", 15, 12, 12, "TIE", "Ev A", "Ev B", "Exp A", "Exp B"),
                    new CategoryComparisonDTO("ATS Compatibility", 10, 8, 9, "B", "Ev A", "Ev B", "Exp A", "Exp B")
            ); // A = 77, B = 83, diff = 6

            var comparison = ResumeComparisonDTO.of(
                    "cmp-2", "A.pdf", "B.pdf", null,
                    categories,
                    List.of("Diff 1", "Diff 2", "Diff 3"),
                    "Takeaway",
                    List.of("Borrow A"),
                    List.of("Borrow B")
            );

            assertEquals(77, comparison.totalScoreA());
            assertEquals(83, comparison.totalScoreB());
            assertEquals(6, comparison.scoreDifference());
            assertEquals("B", comparison.winner());
            assertEquals("Resume B has a narrow overall advantage", comparison.winnerVerdict());
        }

        @Test
        @DisplayName("Difference <= 3 results in 'Too close to call' with empty winner differentiators")
        void differenceThreeOrLessTooCloseToCall() {
            var categories = List.of(
                    new CategoryComparisonDTO("Content & Relevance", 20, 16, 16, "TIE", "Ev A", "Ev B", "Exp A", "Exp B"),
                    new CategoryComparisonDTO("Skills & Keywords", 20, 16, 17, "B", "Ev A", "Ev B", "Exp A", "Exp B"),
                    new CategoryComparisonDTO("Experience & Evidence", 20, 16, 16, "TIE", "Ev A", "Ev B", "Exp A", "Exp B"),
                    new CategoryComparisonDTO("Impact & Achievements", 15, 12, 11, "A", "Ev A", "Ev B", "Exp A", "Exp B"),
                    new CategoryComparisonDTO("Clarity & Structure", 15, 12, 12, "TIE", "Ev A", "Ev B", "Exp A", "Exp B"),
                    new CategoryComparisonDTO("ATS Compatibility", 10, 8, 8, "TIE", "Ev A", "Ev B", "Exp A", "Exp B")
            ); // A = 80, B = 80, diff = 0

            var comparison = ResumeComparisonDTO.of(
                    "cmp-3", "A.pdf", "B.pdf", null,
                    categories,
                    List.of("Diff 1", "Diff 2", "Diff 3"),
                    "Takeaway",
                    List.of("Borrow A"),
                    List.of("Borrow B")
            );

            assertEquals(80, comparison.totalScoreA());
            assertEquals(80, comparison.totalScoreB());
            assertEquals(0, comparison.scoreDifference());
            assertEquals("TIE", comparison.winner());
            assertEquals("Too close to call", comparison.winnerVerdict());
            assertTrue(comparison.winnerDifferentiators().isEmpty(), "Ties/too-close must not have winner differentiators");
        }

        @Test
        @DisplayName("Score calculation sums exactly to the category points")
        void totalScoreIsExactSumOfCategoryScores() {
            var categories = createSampleCategories(79, 86);
            var comparison = ResumeComparisonDTO.of(
                    "cmp-4", "A.pdf", "B.pdf", null,
                    categories,
                    List.of("Diff 1", "Diff 2", "Diff 3"),
                    "Takeaway",
                    List.of("Borrow A"),
                    List.of("Borrow B")
            );

            assertEquals(79, comparison.totalScoreA());
            assertEquals(86, comparison.totalScoreB());
            assertEquals(6, comparison.categories().size());
        }
    }

    @Nested
    @DisplayName("Edge Case Handling")
    class EdgeCaseTests {

        @Test
        @DisplayName("Identical resumes factory sets isIdentical and neutral explanation without winner")
        void identicalResumesHandledSafely() {
            var identical = ResumeComparisonDTO.forIdentical("cmp-ident", "Resume.pdf", "Resume_Copy.pdf", null);

            assertTrue(identical.isIdentical());
            assertFalse(identical.isUnreadable());
            assertEquals(0, identical.totalScoreA());
            assertEquals(0, identical.totalScoreB());
            assertEquals("TIE", identical.winner());
            assertEquals("Identical Resumes", identical.winnerVerdict());
            assertTrue(identical.overallTakeaway().contains("identical"));
            assertTrue(identical.winnerDifferentiators().isEmpty());
        }

        @Test
        @DisplayName("Unreadable resume factory sets isUnreadable with honest reason without scoring as weak")
        void unreadableResumeHandledSafely() {
            var unreadable = ResumeComparisonDTO.forUnreadable(
                    "cmp-unread", "Scan_A.pdf", "Normal_B.pdf",
                    "Resume A contains no readable text or corrupted layers", null
            );

            assertTrue(unreadable.isUnreadable());
            assertFalse(unreadable.isIdentical());
            assertEquals(0, unreadable.totalScoreA());
            assertEquals(0, unreadable.totalScoreB());
            assertEquals("TIE", unreadable.winner());
            assertEquals("Comparison Unavailable", unreadable.winnerVerdict());
            assertEquals("Resume A contains no readable text or corrupted layers", unreadable.unreadableReason());
        }
    }

    @Nested
    @DisplayName("Structured AI Parsing & Validation")
    class StructuredAIParsingTests {

        @Test
        @DisplayName("Valid AI JSON with 6 categories parses cleanly into ResumeComparisonDTO")
        void validAIJsonParsesCleanly() {
            String json = """
            {
              "categories": [
                {"categoryName": "Content & Relevance", "scoreA": 16, "scoreB": 18, "evidenceA": "Lists backend APIs", "evidenceB": "Details high-scale microservices", "explanationA": "Solid", "explanationB": "Exceptional"},
                {"categoryName": "Skills & Keywords", "scoreA": 17, "scoreB": 18, "evidenceA": "Java, Spring Boot", "evidenceB": "Java, Kafka, Redis, Docker", "explanationA": "Good", "explanationB": "Comprehensive"},
                {"categoryName": "Experience & Evidence", "scoreA": 15, "scoreB": 17, "evidenceA": "2 years backend engineer", "evidenceB": "4 years progressive tech lead", "explanationA": "Clear history", "explanationB": "Deep leadership"},
                {"categoryName": "Impact & Achievements", "scoreA": 10, "scoreB": 13, "evidenceA": "Built APIs", "evidenceB": "Reduced latency by 34%", "explanationA": "Task-focused", "explanationB": "Quantified impact"},
                {"categoryName": "Clarity & Structure", "scoreA": 12, "scoreB": 12, "evidenceA": "Clean standard format", "evidenceB": "Clean reverse chronological", "explanationA": "Easy to skim", "explanationB": "Well balanced"},
                {"categoryName": "ATS Compatibility", "scoreA": 9, "scoreB": 8, "evidenceA": "Standard headings", "evidenceB": "Minor columns in header", "explanationA": "Flawless", "explanationB": "Strong"}
              ],
              "winnerDifferentiators": [
                "Stronger evidence of production impact",
                "Broader cloud and distributed systems keywords",
                "Clearer quantified metrics throughout experience"
              ],
              "overallTakeaway": "Resume B demonstrates stronger engineering leadership and quantifiable business results.",
              "borrowFromBForA": [
                "Incorporate quantified metric percentages into backend achievements"
              ],
              "borrowFromAForB": [
                "Adopt linear header formatting for cleaner ATS parsing"
              ]
            }
            """;

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            ResumeComparisonDTO dto = AIResponseParser.parseAndValidateComparison(
                    json, "cmp-test", "Alice.pdf", "Bob.pdf", null, 100000, mapper, "gemini"
            );

            assertNotNull(dto);
            assertEquals("cmp-test", dto.comparisonId());
            assertEquals("Alice.pdf", dto.fileNameA());
            assertEquals("Bob.pdf", dto.fileNameB());
            assertEquals(79, dto.totalScoreA());
            assertEquals(86, dto.totalScoreB());
            assertEquals(7, dto.scoreDifference());
            assertEquals("B", dto.winner());
            assertEquals("Resume B has a narrow overall advantage", dto.winnerVerdict());
            assertEquals(3, dto.winnerDifferentiators().size());
            assertEquals(6, dto.categories().size());
            assertEquals(1, dto.borrowFromBForA().size());
            assertEquals(1, dto.borrowFromAForB().size());
        }

        @Test
        @DisplayName("Malformed JSON throws GeminiResponseException")
        void malformedJsonThrowsParsingException() {
            String badJson = "{ invalid json content here";
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            assertThrows(GeminiResponseException.class, () ->
                    AIResponseParser.parseAndValidateComparison(badJson, "cmp-bad", "A.pdf", "B.pdf", null, 100000, mapper, "gemini")
            );
        }

        @Test
        @DisplayName("Missing categories array throws GeminiResponseException")
        void missingCategoriesArrayThrowsParsingException() {
            String jsonNoCats = """
            {
              "keyDifferentiators": ["Diff 1", "Diff 2", "Diff 3"],
              "overallTakeaway": "Test",
              "resumeABorrowsFromB": ["A"],
              "resumeBBorrowsFromA": ["B"]
            }
            """;

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            assertThrows(GeminiResponseException.class, () ->
                    AIResponseParser.parseAndValidateComparison(jsonNoCats, "cmp-nocats", "A.pdf", "B.pdf", null, 100000, mapper, "gemini")
            );
        }
    }

    @Nested
    @DisplayName("History Architecture Integration")
    class HistoryIntegrationTests {

        @Test
        @DisplayName("Comparison is saved and retrieved from AnalysisSessionStore without rerunning AI")
        void comparisonSessionStoreIntegration() {
            var store = new AnalysisSessionStore(Duration.ofMinutes(30), 100);

            var comparison = ResumeComparisonDTO.of(
                    "cmp-hist-1", "Dhyey_Resume.pdf", "Dhyey_Resume_Final.pdf", "banking sector",
                    createSampleCategories(79, 86),
                    List.of("Stronger metrics", "Better alignment", "Clean structure"),
                    "Resume B leads due to metrics",
                    List.of("Add metrics"),
                    List.of("Add summary")
            );

            store.saveComparison(comparison);

            var retrieved = store.getComparison("cmp-hist-1");
            assertTrue(retrieved.isPresent());
            assertEquals("Dhyey_Resume.pdf", retrieved.get().fileNameA());
            assertEquals("Dhyey_Resume_Final.pdf", retrieved.get().fileNameB());
            assertEquals("banking sector", retrieved.get().jobDescription());
            assertEquals(79, retrieved.get().totalScoreA());
            assertEquals(86, retrieved.get().totalScoreB());

            // Check history items
            var history = store.getRecentCompletedSessions();
            assertEquals(1, history.size());
            var item = history.get(0);
            assertEquals("COMPARE", item.type());
            assertEquals("cmp-hist-1", item.analysisId());
            assertEquals("Dhyey_Resume.pdf", item.fileName());
            assertEquals("Dhyey_Resume_Final.pdf", item.fileNameB());
            assertEquals(79, item.score());
            assertEquals(86, item.scoreB());
            assertEquals("Resume B has a narrow overall advantage", item.verdict());
            assertEquals("banking sector", item.jobContext());
        }
    }

    @Nested
    @DisplayName("AI Provider Fallback for Comparison")
    class ProviderFallbackTests {

        @Test
        @DisplayName("When primary provider succeeds, fallback provider is not invoked")
        void primarySuccessBypassesFallback() {
            var service = new FallbackAIService(primaryProvider, fallbackProvider);
            var categories = createSampleCategories(79, 86);
            var comparison = ResumeComparisonDTO.of(
                    "cmp-fallback-1", "A.pdf", "B.pdf", null,
                    categories, List.of("D1", "D2", "D3"), "Takeaway", List.of("B1"), List.of("B2")
            );

            when(primaryProvider.getProviderName()).thenReturn("gemini");
            when(primaryProvider.compareResumes(eq("textA"), eq("textB"), any(), eq("cmp-1"), eq("A.pdf"), eq("B.pdf"), any()))
                    .thenReturn(comparison);

            AIComparisonResult result = service.compareResumesWithProvider(
                    "textA", "textB", null, "cmp-1", "A.pdf", "B.pdf", status -> {}
            );

            assertNotNull(result);
            assertEquals("gemini", result.providerName());
            assertEquals(79, result.comparison().totalScoreA());
            assertEquals(86, result.comparison().totalScoreB());
            verify(fallbackProvider, never()).compareResumes(any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("When primary throws AICommunicationException, fallback provider is invoked")
        void primaryFailureTriggersFallback() {
            var service = new FallbackAIService(primaryProvider, fallbackProvider);
            var categories = createSampleCategories(79, 86);
            var comparison = ResumeComparisonDTO.of(
                    "cmp-fallback-2", "A.pdf", "B.pdf", null,
                    categories, List.of("D1", "D2", "D3"), "Takeaway", List.of("B1"), List.of("B2")
            );

            when(primaryProvider.getProviderName()).thenReturn("gemini");
            when(fallbackProvider.isAvailable()).thenReturn(true);
            when(primaryProvider.compareResumes(any(), any(), any(), any(), any(), any(), any()))
                    .thenThrow(new AICommunicationException("gemini", "Gemini quota exceeded", true, null));

            when(fallbackProvider.getProviderName()).thenReturn("groq");
            when(fallbackProvider.compareResumes(eq("textA"), eq("textB"), any(), eq("cmp-2"), eq("A.pdf"), eq("B.pdf"), any()))
                    .thenReturn(comparison);

            AIComparisonResult result = service.compareResumesWithProvider(
                    "textA", "textB", null, "cmp-2", "A.pdf", "B.pdf", status -> {}
            );

            assertNotNull(result);
            assertEquals("groq", result.providerName());
            assertEquals(86, result.comparison().totalScoreB());
            verify(fallbackProvider).compareResumes(eq("textA"), eq("textB"), any(), eq("cmp-2"), eq("A.pdf"), eq("B.pdf"), any());
        }

        @Test
        @DisplayName("When both providers fail, AICommunicationException is thrown")
        void bothProvidersFailThrowsException() {
            var service = new FallbackAIService(primaryProvider, fallbackProvider);

            when(primaryProvider.getProviderName()).thenReturn("gemini");
            when(fallbackProvider.isAvailable()).thenReturn(true);
            when(primaryProvider.compareResumes(any(), any(), any(), any(), any(), any(), any()))
                    .thenThrow(new AICommunicationException("gemini", "Gemini down", true, null));

            when(fallbackProvider.getProviderName()).thenReturn("groq");
            when(fallbackProvider.compareResumes(any(), any(), any(), any(), any(), any(), any()))
                    .thenThrow(new AICommunicationException("groq", "Groq down", false, null));

            assertThrows(AICommunicationException.class, () ->
                    service.compareResumesWithProvider("textA", "textB", null, "cmp-3", "A.pdf", "B.pdf", status -> {})
            );
        }
    }
}
