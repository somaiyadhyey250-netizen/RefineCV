package com.resumeanalyzer.resume_analyzer;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "refinecv.analysis.rate-limit.max-requests=1",
        "refinecv.analysis.rate-limit.window=1h",
        "refinecv.analysis.rate-limit.max-tracked-clients=10"
})
@AutoConfigureMockMvc
class ResumeRateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ResumeTextExtractor resumeTextExtractor;

    @MockitoBean
    private GeminiService geminiService;

    @MockitoBean
    private GroqAIProvider groqAIProvider;

    @Test
    void allowsNormalRequestThenRejectsExcessWithSafeSseMessage() throws Exception {
        when(resumeTextExtractor.extractText(any(byte[].class), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<String> progress = invocation.getArgument(1);
            progress.accept("extracting");
            progress.accept("extracted");
            return "resume text";
        });
        when(groqAIProvider.analyzeResume(eq("resume text"), any()))
                .thenReturn(new ResumeAnalysisDTO(80, "Strong resume", List.of(), List.of(), List.of(),
                        List.of(), "Good", List.of(), List.of()));

        String allowedBody = performUpload();
        assertTrue(allowedBody.contains("event:result"));

        String rejectedBody = performUpload();
        assertTrue(rejectedBody.contains("event:error"));
        assertTrue(rejectedBody.contains("You're making requests too quickly"));
        assertTrue(!rejectedBody.contains("event:result"));
        verify(groqAIProvider).analyzeResume(eq("resume text"), any());
        verify(geminiService, org.mockito.Mockito.never()).analyzeResume(any(), any());
    }

    private String performUpload() throws Exception {
        MvcResult started = mockMvc.perform(multipart("/analyze").file(
                        new MockMultipartFile("resume", "resume.pdf", "application/pdf", new byte[]{1})))
                .andExpect(request().asyncStarted())
                .andReturn();
        return mockMvc.perform(asyncDispatch(started)).andReturn().getResponse().getContentAsString();
    }
}
