package com.resumeanalyzer.resume_analyzer;

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
class GoogleVerificationEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GeminiService geminiService;

    @MockitoBean
    private GroqAIProvider groqAIProvider;

    @MockitoBean
    private ResumeTextExtractor resumeTextExtractor;

    @Test
    @DisplayName("GET /google968eb9ab4d26e793.html returns HTTP 200 with exact verification token")
    void verificationFileIsServedFromRootWithExactContent() throws Exception {
        mockMvc.perform(get("/google968eb9ab4d26e793.html"))
                .andExpect(status().isOk())
                .andExpect(content().string("google-site-verification: google968eb9ab4d26e793.html"));
    }
}
