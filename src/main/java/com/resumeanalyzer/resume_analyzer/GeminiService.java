package com.resumeanalyzer.resume_analyzer;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Schema;

@Service
public class GeminiService {


    /*
     * Existing method.
     *
     * Keeps compatibility with any code
     * that calls analyzeResume(resumeText).
     */
    public String analyzeResume(
            String resumeText
    ) {

        return analyzeResume(
                resumeText,
                status -> {
                    // No progress listener required.
                }
        );

    }


    /*
     * New method with progress reporting.
     *
     * The controller will use this method
     * to know what Gemini is currently doing.
     */
    public String analyzeResume(
            String resumeText,
            Consumer<String> progress
    ) {


        // =========================================
        // GET API KEY
        // =========================================

        String apiKey =
                System.getenv(
                        "GEMINI_API_KEY"
                );


        if (
            apiKey == null ||
            apiKey.trim().isEmpty()
        ) {

            throw new IllegalStateException(
                    "GEMINI_API_KEY environment variable is not set."
            );

        }


        // =========================================
        // CREATE GEMINI CLIENT
        // =========================================

        Client client =
                Client.builder()
                        .apiKey(apiKey)
                        .build();


        // =========================================
        // RESPONSE JSON STRUCTURE
        // =========================================

        Schema resumeSchema =
                Schema.builder()

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


        // =========================================
        // AI PROMPT
        // =========================================

        String prompt = """

                You are an expert AI Resume Analyzer.

                Analyze the resume provided below.

                IMPORTANT RULES:

                1. Do not invent information.

                2. Use only information present
                   in the resume.

                3. The resume may contain OCR errors.

                4. Correct obvious OCR mistakes only
                   when the intended meaning is clear.

                5. If something cannot be determined,
                   say "Not mentioned".

                6. Give honest and practical feedback.

                7. Do not exaggerate skills,
                   experience, education or achievements.

                8. The score must be between 0 and 100.

                9. Return the analysis according
                   to the requested JSON structure.

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


        // =========================================
        // GEMINI CONFIGURATION
        // =========================================

        GenerateContentConfig config =
                GenerateContentConfig.builder()

                        .responseMimeType(
                                "application/json"
                        )

                        .responseSchema(
                                resumeSchema
                        )

                        .build();


        // =========================================
        // AI ANALYSIS START
        // =========================================

        /*
         * IMPORTANT:
         *
         * This status is sent immediately before
         * the actual Gemini request.
         *
         * Therefore the loading UI can show
         * "AI analysis" while Gemini is actually
         * processing the resume.
         */

        progress.accept(
                "ai-analysis"
        );


        // =========================================
        // ACTUAL GEMINI REQUEST
        // =========================================

        GenerateContentResponse response =
                client.models.generateContent(

                        "gemini-3.6-flash",

                        prompt,

                        config

                );


        // =========================================
        // AI RESPONSE RECEIVED
        // =========================================

        /*
         * Gemini has finished generating the
         * analysis.
         *
         * Now the next UI stage can begin.
         */

        progress.accept(
                "recommendations"
        );


        String result =
                response.text();


        // =========================================
        // RETURN FINAL JSON
        // =========================================

        return result;

    }

}