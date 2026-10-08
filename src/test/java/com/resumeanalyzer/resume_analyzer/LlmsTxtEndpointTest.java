package com.resumeanalyzer.resume_analyzer;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "refinecv.analysis.timeout=2s",
        "refinecv.analysis.rate-limit.max-requests=100"
})
@AutoConfigureMockMvc
class LlmsTxtEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GeminiService geminiService;

    @MockitoBean
    private GroqAIProvider groqAIProvider;

    @MockitoBean
    private ResumeTextExtractor resumeTextExtractor;

    @Test
    @DisplayName("GET /llms.txt returns HTTP 200 with RefineCV AI discoverability content")
    void llmsTxtIsServedFromRootWithExpectedContent() throws Exception {
        mockMvc.perform(get("/llms.txt"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(org.springframework.http.MediaType.TEXT_PLAIN))
                .andExpect(content().string(containsString("RefineCV")))
                .andExpect(content().string(containsString("Analyze Resume")))
                .andExpect(content().string(containsString("Interview Prep from CV")));
    }
}
