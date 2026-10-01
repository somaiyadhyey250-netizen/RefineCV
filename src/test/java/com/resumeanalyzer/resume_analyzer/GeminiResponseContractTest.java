package com.resumeanalyzer.resume_analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class GeminiResponseContractTest {

    private final GeminiService service = new GeminiService(2_000, "");

    @Test
    void mapsCompletePayloadAndIgnoresUnknownFieldsForForwardCompatibility() {
        ResumeAnalysisDTO result = service.parseAndValidateResponse(validJson(0)
                .replace("}", ",\"futureField\":\"ignored\"}"));

        assertEquals(0, result.score());
        assertEquals("Not mentioned", result.summary());
        assertEquals("Good", result.atsCompatibility());
    }

    @Test
    void acceptsMaximumScore() {
        assertEquals(100, service.parseAndValidateResponse(validJson(100)).score());
    }

    @Test
    void rejectsMalformedJson() {
        GeminiResponseException error = assertThrows(GeminiResponseException.class,
                () -> service.parseAndValidateResponse("{broken"));
        assertEquals(GeminiResponseException.Reason.MALFORMED_JSON, error.getReason());
    }

    @Test
    void rejectsMissingFieldsAndWrongFieldTypes() {
        assertThrows(GeminiResponseException.class,
                () -> service.parseAndValidateResponse("{\"score\":80}"));
        assertThrows(GeminiResponseException.class,
                () -> service.parseAndValidateResponse(validJson(80).replace("\"score\":80", "\"score\":\"80\"")));
    }

    @Test
    void rejectsScoresOutsideRangeAndBlankRequiredText() {
        assertThrows(AnalysisContractValidationException.class,
                () -> service.parseAndValidateResponse(validJson(-1)));
        assertThrows(AnalysisContractValidationException.class,
                () -> service.parseAndValidateResponse(validJson(101)));
        assertThrows(AnalysisContractValidationException.class,
                () -> service.parseAndValidateResponse(validJson(80).replace("Not mentioned", " ")));
    }

    @Test
    void rejectsOversizedPayloadAndTrailingJsonValues() {
        GeminiService smallLimit = new GeminiService(10, "");
        assertEquals(GeminiResponseException.Reason.RESPONSE_TOO_LARGE,
                assertThrows(GeminiResponseException.class,
                        () -> smallLimit.parseAndValidateResponse(validJson(80))).getReason());
        assertThrows(GeminiResponseException.class,
                () -> service.parseAndValidateResponse(validJson(80) + " {}"));
    }

    private String validJson(int score) {
        return """
                {"score":%d,"summary":"Not mentioned","strongestSkills":[],
                "missingOrWeakSkills":[],"strengths":[],"weaknesses":[],
                "atsCompatibility":"Good","suggestions":[],"recommendedChanges":[]}
                """.formatted(score);
    }
}
