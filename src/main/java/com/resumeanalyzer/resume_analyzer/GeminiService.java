package com.resumeanalyzer.resume_analyzer;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Schema;

@Service
public class GeminiService {

    public String analyzeResume(String resumeText) {

        String apiKey = System.getenv("GEMINI_API_KEY");

        Client client = Client.builder()
                .apiKey(apiKey)
                .build();

        // Structure of the JSON we want from Gemini
        Schema resumeSchema = Schema.builder()
                .type("OBJECT")
                .properties(
                        Map.of(
                                "score",
                                Schema.builder()
                                        .type("INTEGER")
                                        .build(),

                                "summary",
                                Schema.builder()
                                        .type("STRING")
                                        .build(),

                                "strongestSkills",
                                Schema.builder()
                                        .type("ARRAY")
                                        .items(
                                                Schema.builder()
                                                        .type("STRING")
                                                        .build()
                                        )
                                        .build(),

                                "missingOrWeakSkills",
                                Schema.builder()
                                        .type("ARRAY")
                                        .items(
                                                Schema.builder()
                                                        .type("STRING")
                                                        .build()
                                        )
                                        .build(),

                                "strengths",
                                Schema.builder()
                                        .type("ARRAY")
                                        .items(
                                                Schema.builder()
                                                        .type("STRING")
                                                        .build()
                                        )
                                        .build(),

                                "weaknesses",
                                Schema.builder()
                                        .type("ARRAY")
                                        .items(
                                                Schema.builder()
                                                        .type("STRING")
                                                        .build()
                                        )
                                        .build(),

                                "atsCompatibility",
                                Schema.builder()
                                        .type("STRING")
                                        .build(),

                                "suggestions",
                                Schema.builder()
                                        .type("ARRAY")
                                        .items(
                                                Schema.builder()
                                                        .type("STRING")
                                                        .build()
                                        )
                                        .build(),

                                "recommendedChanges",
                                Schema.builder()
                                        .type("ARRAY")
                                        .items(
                                                Schema.builder()
                                                        .type("STRING")
                                                        .build()
                                        )
                                        .build()
                        )
                )
                .required(
                        List.of(
                                "score",
                                "summary",
                                "strongestSkills",
                                "missingOrWeakSkills",
                                "strengths",
                                "weaknesses",
                                "atsCompatibility",
                                "suggestions",
                                "recommendedChanges"
                        )
                )
                .build();

        String prompt = """
                You are an expert AI Resume Analyzer.

                Analyze the resume provided below.

                IMPORTANT RULES:

                1. Do not invent information.
                2. Use only information present in the resume.
                3. The resume may contain OCR errors.
                4. Correct obvious OCR mistakes only when the intended meaning
                   is clear.
                5. If something cannot be determined, say "Not mentioned".
                6. Give honest and practical feedback.
                7. Do not exaggerate skills, experience, education or achievements.
                8. The score must be between 0 and 100.
                9. Return the analysis according to the requested JSON structure.

                Analyze:

                - Overall resume quality
                - Strongest skills
                - Missing or weak skills
                - Strengths
                - Weaknesses
                - ATS compatibility
                - Practical improvement suggestions
                - Recommended resume changes

                Resume Text:
                --------------------
                """ + resumeText + """
                --------------------
                """;

        GenerateContentConfig config =
                GenerateContentConfig.builder()
                        .responseMimeType("application/json")
                        .responseSchema(resumeSchema)
                        .build();

        GenerateContentResponse response =
                client.models.generateContent(
                        "gemini-3.6-flash",
                        prompt,
                        config
                );

        return response.text();
    }
}