package com.resumeanalyzer.resume_analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class GeminiImprovementContractTest {

    private final GeminiService service = new GeminiService(2_000, "");

    @Test
    void mapsCompleteImprovementPayloadAndIgnoresUnknownFields() {
        ResumeImprovementDTO result = service.parseAndValidateImprovementResponse(
                validImprovementJson().replace("}", ",\"futureImprovementFlag\":\"ignored\"}")
        );

        assertEquals("Experienced software engineer with 5+ years building distributed services.",
                result.improvedSummary());
        assertEquals(1, result.bulletImprovements().size());
        BulletImprovementDTO bullet = result.bulletImprovements().get(0);
        assertEquals("Experience", bullet.section());
        assertEquals("Helped with api development", bullet.original());
        assertEquals("Engineered RESTful microservices reducing response latency by streamlining database queries",
                bullet.improved());
        assertEquals("Replaced passive verb with strong technical action verb and clarified scope", bullet.explanation());
        assertEquals(List.of("Enhanced action verbs throughout"), result.improvementExplanations());
        assertEquals(List.of("Verify metric details match your exact production results"), result.actionableChanges());
    }

    @Test
    void rejectsMalformedJsonInImprovementResponse() {
        GeminiResponseException error = assertThrows(GeminiResponseException.class,
                () -> service.parseAndValidateImprovementResponse("{unclosed json"));
        assertEquals(GeminiResponseException.Reason.MALFORMED_JSON, error.getReason());
    }

    @Test
    void rejectsMissingRequiredFields() {
        assertThrows(GeminiResponseException.class,
                () -> service.parseAndValidateImprovementResponse("{\"improvedSummary\":\"Summary only\"}"));
    }

    @Test
    void rejectsWrongFieldTypes() {
        String wrongTypeJson = """
                {
                  "improvedSummary": 12345,
                  "bulletImprovements": [
                    {
                      "section": "Experience",
                      "original": "Helped with api development",
                      "improved": "Engineered RESTful microservices",
                      "explanation": "Active verb"
                    }
                  ],
                  "improvementExplanations": ["Enhanced action verbs"],
                  "actionableChanges": ["Review bullets"]
                }
                """;
        assertThrows(GeminiResponseException.class,
                () -> service.parseAndValidateImprovementResponse(wrongTypeJson));
    }

    @Test
    void rejectsBlankRequiredText() {
        String blankSummary = """
                {
                  "improvedSummary": "    ",
                  "bulletImprovements": [
                    {
                      "section": "Experience",
                      "original": "Helped with api development",
                      "improved": "Engineered RESTful microservices",
                      "explanation": "Active verb"
                    }
                  ],
                  "improvementExplanations": ["Enhanced action verbs"],
                  "actionableChanges": ["Review bullets"]
                }
                """;
        AnalysisContractValidationException error = assertThrows(AnalysisContractValidationException.class,
                () -> service.parseAndValidateImprovementResponse(blankSummary));
        assertEquals(AnalysisContractValidationException.Reason.BLANK_REQUIRED_TEXT, error.getReason());
    }

    @Test
    void rejectsEmptyBulletList() {
        String emptyBullets = """
                {
                  "improvedSummary": "Experienced software engineer.",
                  "bulletImprovements": [],
                  "improvementExplanations": ["Enhanced action verbs"],
                  "actionableChanges": ["Review bullets"]
                }
                """;
        AnalysisContractValidationException error = assertThrows(AnalysisContractValidationException.class,
                () -> service.parseAndValidateImprovementResponse(emptyBullets));
        assertEquals(AnalysisContractValidationException.Reason.BLANK_REQUIRED_TEXT, error.getReason());
    }

    @Test
    void rejectsBlankBulletFields() {
        String blankBullet = """
                {
                  "improvedSummary": "Experienced software engineer.",
                  "bulletImprovements": [
                    {
                      "section": "   ",
                      "original": "Helped with api development",
                      "improved": "Engineered RESTful microservices",
                      "explanation": "Active verb"
                    }
                  ],
                  "improvementExplanations": ["Enhanced action verbs"],
                  "actionableChanges": ["Review bullets"]
                }
                """;
        AnalysisContractValidationException error = assertThrows(AnalysisContractValidationException.class,
                () -> service.parseAndValidateImprovementResponse(blankBullet));
        assertEquals(AnalysisContractValidationException.Reason.BLANK_REQUIRED_TEXT, error.getReason());
    }

    @Test
    void rejectsBlankListItemInExplanationsOrActions() {
        String blankAction = """
                {
                  "improvedSummary": "Experienced software engineer.",
                  "bulletImprovements": [
                    {
                      "section": "Experience",
                      "original": "Helped with api development",
                      "improved": "Engineered RESTful microservices",
                      "explanation": "Active verb"
                    }
                  ],
                  "improvementExplanations": ["Enhanced action verbs"],
                  "actionableChanges": ["   "]
                }
                """;
        AnalysisContractValidationException error = assertThrows(AnalysisContractValidationException.class,
                () -> service.parseAndValidateImprovementResponse(blankAction));
        assertEquals(AnalysisContractValidationException.Reason.BLANK_LIST_ITEM, error.getReason());
    }

    @Test
    void rejectsOversizedImprovementPayload() {
        GeminiService smallLimit = new GeminiService(20, "");
        GeminiResponseException error = assertThrows(GeminiResponseException.class,
                () -> smallLimit.parseAndValidateImprovementResponse(validImprovementJson()));
        assertEquals(GeminiResponseException.Reason.RESPONSE_TOO_LARGE, error.getReason());
    }

    @Test
    void rejectsTrailingJsonInImprovementResponse() {
        assertThrows(GeminiResponseException.class,
                () -> service.parseAndValidateImprovementResponse(validImprovementJson() + " {}"));
    }

    private String validImprovementJson() {
        return """
                {
                  "improvedSummary": "Experienced software engineer with 5+ years building distributed services.",
                  "bulletImprovements": [
                    {
                      "section": "Experience",
                      "original": "Helped with api development",
                      "improved": "Engineered RESTful microservices reducing response latency by streamlining database queries",
                      "explanation": "Replaced passive verb with strong technical action verb and clarified scope"
                    }
                  ],
                  "improvementExplanations": [
                    "Enhanced action verbs throughout"
                  ],
                  "actionableChanges": [
                    "Verify metric details match your exact production results"
                  ]
                }
                """;
    }
}
