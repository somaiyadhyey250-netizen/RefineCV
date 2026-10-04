package com.resumeanalyzer.resume_analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "refinecv.analysis.timeout=2s",
        "refinecv.analysis.rate-limit.max-requests=100"
})
@AutoConfigureMockMvc
class ClearHistoryFeatureTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AnalysisSessionStore sessionStore;

    @MockitoBean
    private GeminiService geminiService;

    @MockitoBean
    private ResumeTextExtractor resumeTextExtractor;

    private final ResumeAnalysisDTO sampleAnalysis = new ResumeAnalysisDTO(
            82,
            "Solid general resume",
            List.of("Java", "Spring Boot"),
            List.of("Kubernetes"),
            List.of("Backend architecture"),
            List.of("Passive bullet points"),
            "Good",
            List.of("Active phrasing"),
            List.of("Quantify metrics")
    );

    @BeforeEach
    void setUp() {
        sessionStore.clear();
    }

    private ResumeComparisonDTO createSampleComparison(String id, String fileA, String fileB, String job) {
        return ResumeComparisonDTO.of(
                id, fileA, fileB, job,
                List.of(
                        new CategoryComparisonDTO("Content & Relevance", 20, 18, 12, "A", "Ev A", "Ev B", "Exp A", "Exp B"),
                        new CategoryComparisonDTO("Skills & Keywords", 20, 18, 12, "A", "Ev A", "Ev B", "Exp A", "Exp B"),
                        new CategoryComparisonDTO("Experience & Evidence", 20, 18, 12, "A", "Ev A", "Ev B", "Exp A", "Exp B"),
                        new CategoryComparisonDTO("Impact & Achievements", 15, 12, 10, "A", "Ev A", "Ev B", "Exp A", "Exp B"),
                        new CategoryComparisonDTO("Clarity & Structure", 15, 12, 12, "TIE", "Ev A", "Ev B", "Exp A", "Exp B"),
                        new CategoryComparisonDTO("ATS Compatibility", 10, 8, 8, "TIE", "Ev A", "Ev B", "Exp A", "Exp B")
                ),
                List.of("B has higher impact metrics"),
                "Resume B has a slight edge overall",
                List.of("Include more numbers"),
                List.of("Add certifications")
        );
    }

    @Test
    @DisplayName("1 & 10. History endpoint returns records and sessions can be reopened before clearing")
    void testHistoryReturnsRecordsAndCanBeReopened() throws Exception {
        sessionStore.completeSession("sess-gen-1", "Resume text", sampleAnalysis, "gemini",
                AnalysisMode.GENERAL, null, "Alice_Resume.pdf");
        sessionStore.completeSession("sess-job-1", "Resume text", sampleAnalysis, "gemini",
                AnalysisMode.SPECIFIC_JOB, "Java Developer role", "Bob_Resume.pdf");
        sessionStore.saveComparison(createSampleComparison("cmp-pair-1", "A.pdf", "B.pdf", "Fintech role"));

        // GET /api/history returns all 3 records
        mockMvc.perform(get("/api/history").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        // Verify reopening views before clearing
        mockMvc.perform(get("/analysis/sess-gen-1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/job-analysis/sess-job-1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/compare/cmp-pair-1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("2, 4, 5, 6. DELETE /api/history clears general analysis, job analysis, and compare records")
    void testDeleteHistoryClearsAllUserVisibleRecords() throws Exception {
        sessionStore.completeSession("sess-gen-1", "Resume text", sampleAnalysis, "gemini",
                AnalysisMode.GENERAL, null, "Alice_Resume.pdf");
        sessionStore.completeSession("sess-job-1", "Resume text", sampleAnalysis, "gemini",
                AnalysisMode.SPECIFIC_JOB, "Java Developer role", "Bob_Resume.pdf");
        sessionStore.saveComparison(createSampleComparison("cmp-pair-1", "A.pdf", "B.pdf", "Fintech role"));

        assertEquals(3, sessionStore.getRecentCompletedSessions().size());

        // DELETE /api/history
        mockMvc.perform(delete("/api/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("History cleared"));

        // GET /api/history should now be empty
        mockMvc.perform(get("/api/history").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        assertEquals(0, sessionStore.getRecentCompletedSessions().size());
    }

    @Test
    @DisplayName("3. Clearing history preserves ongoing active processing sessions")
    void testClearHistoryPreservesActiveProcessingSessions() throws Exception {
        // Start an active analysis session in PROCESSING state
        sessionStore.startSession("active-sess-99");
        assertTrue(sessionStore.getStatus("active-sess-99").isPresent());
        assertEquals(AnalysisStatus.PROCESSING, sessionStore.getStatus("active-sess-99").get().status());

        // Also add a completed session
        sessionStore.completeSession("sess-done-1", "Resume text", sampleAnalysis, "gemini",
                AnalysisMode.GENERAL, null, "Alice.pdf");

        // Clear history
        mockMvc.perform(delete("/api/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Active processing session must still be intact
        assertTrue(sessionStore.getStatus("active-sess-99").isPresent());
        assertEquals(AnalysisStatus.PROCESSING, sessionStore.getStatus("active-sess-99").get().status());

        // Completed session was removed from history
        mockMvc.perform(get("/api/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("7 & 8. Internal cmp-* sessions and demo records are never exposed in history")
    void testInternalCmpAndDemoNeverExposed() throws Exception {
        // Internal comparison sub-session
        sessionStore.completeSession("cmp-sub-12345", "Internal sub text", sampleAnalysis, "gemini");
        // Demo session
        sessionStore.completeSession("demo", "Demo text", sampleAnalysis, "gemini");

        // History must be empty
        mockMvc.perform(get("/api/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // After clearing history, still empty and no leakage
        mockMvc.perform(delete("/api/history"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("9. New history can be created after clearing")
    void testNewHistoryCanBeCreatedAfterClearing() throws Exception {
        sessionStore.completeSession("old-sess", "Old text", sampleAnalysis, "gemini",
                AnalysisMode.GENERAL, null, "Old.pdf");

        // Clear history
        mockMvc.perform(delete("/api/history"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/history"))
                .andExpect(jsonPath("$.length()").value(0));

        // Create new comparison and new analysis
        sessionStore.completeSession("new-sess", "New text", sampleAnalysis, "gemini",
                AnalysisMode.GENERAL, null, "New_Resume.pdf");
        sessionStore.saveComparison(createSampleComparison("new-cmp", "NewA.pdf", "NewB.pdf", null));

        // History now has the 2 new items
        mockMvc.perform(get("/api/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("11. Clear operation is idempotent")
    void testClearOperationIsIdempotent() throws Exception {
        // Calling DELETE /api/history on empty store
        mockMvc.perform(delete("/api/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Calling it again
        mockMvc.perform(delete("/api/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("12. AnalysisSessionStore direct clearHistory tests")
    void testDirectSessionStoreClearHistory() {
        var store = new AnalysisSessionStore(Duration.ofMinutes(30), 100);

        store.completeSession("s1", "text", sampleAnalysis, "gemini", AnalysisMode.GENERAL, null, "Resume1.pdf");
        store.completeSession("s2", "text", sampleAnalysis, "gemini", AnalysisMode.SPECIFIC_JOB, "Job", "Resume2.pdf");
        store.saveComparison(createSampleComparison("c1", "A.pdf", "B.pdf", "role"));

        assertEquals(3, store.getRecentCompletedSessions().size());

        store.clearHistory();

        assertEquals(0, store.getRecentCompletedSessions().size());
        assertFalse(store.getComparison("c1").isPresent());
    }
}
