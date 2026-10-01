package com.resumeanalyzer.resume_analyzer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "refinecv.analysis.timeout=2s",
        "refinecv.analysis.rate-limit.max-requests=100"
})
@AutoConfigureMockMvc
class ResumeImprovementEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AnalysisSessionStore sessionStore;

    @MockitoBean
    private GeminiService geminiService;

    @MockitoBean
    private ResumeTextExtractor resumeTextExtractor;

    private final ResumeAnalysisDTO sampleAnalysis = new ResumeAnalysisDTO(
            85,
            "Solid technical profile",
            List.of("Java", "Spring"),
            List.of("Docker"),
            List.of("System design"),
            List.of("Passive voice"),
            "Good",
            List.of("Use active verbs"),
            List.of("Quantify latency reductions")
    );

    private final ResumeImprovementDTO sampleImprovement = new ResumeImprovementDTO(
            "Accomplished Software Engineer with strong background in backend systems.",
            List.of(new BulletImprovementDTO(
                    "Experience",
                    "Worked on database queries",
                    "Optimized SQL queries and indexed high-volume tables to improve lookup times",
                    "Replaced weak verb with specific technical contribution"
            )),
            List.of("Replaced passive language with active verbs"),
            List.of("Review updated bullet points for personal accuracy")
    );

    @BeforeEach
    void setUp() {
        sessionStore.clear();
    }

    @Test
    void improvementSucceedsWithCachedSessionId() throws Exception {
        String analysisId = "session-12345";
        sessionStore.put(analysisId, "Sample extracted resume text", sampleAnalysis);

        when(geminiService.improveResume(eq("Sample extracted resume text"), any()))
                .thenReturn(sampleImprovement);

        MvcResult result = mockMvc.perform(post("/improve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"analysisId\":\"" + analysisId + "\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.improvedSummary").value(sampleImprovement.improvedSummary()))
                .andExpect(jsonPath("$.bulletImprovements[0].improved")
                        .value("Optimized SQL queries and indexed high-volume tables to improve lookup times"))
                .andExpect(jsonPath("$.bulletImprovements[0].section").value("Experience"))
                .andExpect(jsonPath("$.improvementExplanations[0]").value("Replaced passive language with active verbs"));
    }

    @Test
    void improvementSucceedsWithDirectResumeTextAndAnalysis() throws Exception {
        when(geminiService.improveResume(eq("Direct resume text"), any()))
                .thenReturn(sampleImprovement);

        String payload = """
                {
                  "resumeText": "Direct resume text",
                  "analysis": {
                    "score": 85,
                    "summary": "Solid profile",
                    "strongestSkills": ["Java"],
                    "missingOrWeakSkills": [],
                    "strengths": ["Leadership"],
                    "weaknesses": [],
                    "atsCompatibility": "High",
                    "suggestions": [],
                    "recommendedChanges": []
                  }
                }
                """;

        MvcResult result = mockMvc.perform(post("/improve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.improvedSummary").value(sampleImprovement.improvedSummary()));
    }

    @Test
    void missingOrExpiredSessionReturnsBadRequest() throws Exception {
        MvcResult result = mockMvc.perform(post("/improve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"analysisId\":\"nonexistent-session\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Analysis session expired or not found. Please re-analyze your resume."));
    }

    @Test
    void emptyPayloadReturnsBadRequest() throws Exception {
        MvcResult result = mockMvc.perform(post("/improve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Analysis session expired or not found. Please re-analyze your resume."));
    }

    @Test
    void geminiFailureReturnsSafeErrorResponse() throws Exception {
        String analysisId = "session-err";
        sessionStore.put(analysisId, "Resume text", sampleAnalysis);

        when(geminiService.improveResume(any(), any()))
                .thenThrow(new GeminiCommunicationException("Connection failed", null));

        MvcResult result = mockMvc.perform(post("/improve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"analysisId\":\"" + analysisId + "\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Failed to generate resume improvements. Please try again."));
    }
}
