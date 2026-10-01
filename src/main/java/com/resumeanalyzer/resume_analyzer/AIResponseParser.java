package com.resumeanalyzer.resume_analyzer;

import java.util.List;
import java.util.function.Predicate;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Shared multi-layer validation and parsing logic across all AI providers. */
public final class AIResponseParser {

    private AIResponseParser() {
    }

    public static String cleanJson(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    public static ResumeAnalysisDTO parseAndValidateAnalysis(
            String rawResponse,
            int maxResponseCharacters,
            ObjectMapper objectMapper,
            String providerName
    ) {
        String clean = cleanJson(rawResponse);
        if (clean.isBlank()) {
            throw new GeminiResponseException(GeminiResponseException.Reason.MALFORMED_JSON,
                    providerName + " returned an empty response.");
        }
        if (clean.length() > maxResponseCharacters) {
            throw new GeminiResponseException(GeminiResponseException.Reason.RESPONSE_TOO_LARGE,
                    providerName + " response exceeded the configured size limit.");
        }

        final JsonNode root;
        try {
            root = objectMapper.readerFor(JsonNode.class)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readValue(clean);
        } catch (Exception e) {
            throw new GeminiResponseException(GeminiResponseException.Reason.MALFORMED_JSON,
                    providerName + " returned malformed JSON.", e);
        }
        if (root == null || !root.isObject()) {
            throw new GeminiResponseException(GeminiResponseException.Reason.INVALID_STRUCTURE,
                    "Expected a JSON object.");
        }

        requireType(root, "score", JsonNode::isIntegralNumber);
        requireType(root, "summary", JsonNode::isTextual);
        requireType(root, "strongestSkills", JsonNode::isArray);
        requireType(root, "missingOrWeakSkills", JsonNode::isArray);
        requireType(root, "strengths", JsonNode::isArray);
        requireType(root, "weaknesses", JsonNode::isArray);
        requireType(root, "atsCompatibility", JsonNode::isTextual);
        requireType(root, "suggestions", JsonNode::isArray);
        requireType(root, "recommendedChanges", JsonNode::isArray);
        for (String field : List.of("strongestSkills", "missingOrWeakSkills", "strengths",
                "weaknesses", "suggestions", "recommendedChanges")) {
            for (JsonNode item : root.get(field)) {
                if (!item.isTextual()) {
                    throw new GeminiResponseException(GeminiResponseException.Reason.INVALID_STRUCTURE,
                            "Array field '" + field + "' must contain only strings.");
                }
            }
        }

        final ResumeAnalysisDTO dto;
        try {
            dto = objectMapper.treeToValue(root, ResumeAnalysisDTO.class);
        } catch (Exception e) {
            throw new GeminiResponseException(GeminiResponseException.Reason.INVALID_STRUCTURE,
                    providerName + " response could not be mapped to the analysis contract.", e);
        }
        validateAnalysisDTO(dto);
        return dto;
    }

    public static ResumeImprovementDTO parseAndValidateImprovement(
            String rawResponse,
            int maxResponseCharacters,
            ObjectMapper objectMapper,
            String providerName
    ) {
        String clean = cleanJson(rawResponse);
        if (clean.isBlank()) {
            throw new GeminiResponseException(GeminiResponseException.Reason.MALFORMED_JSON,
                    providerName + " returned an empty improvement response.");
        }
        if (clean.length() > maxResponseCharacters) {
            throw new GeminiResponseException(GeminiResponseException.Reason.RESPONSE_TOO_LARGE,
                    providerName + " improvement response exceeded the configured size limit.");
        }

        final JsonNode root;
        try {
            root = objectMapper.readerFor(JsonNode.class)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readValue(clean);
        } catch (Exception e) {
            throw new GeminiResponseException(GeminiResponseException.Reason.MALFORMED_JSON,
                    providerName + " returned malformed JSON in improvement response.", e);
        }
        if (root == null || !root.isObject()) {
            throw new GeminiResponseException(GeminiResponseException.Reason.INVALID_STRUCTURE,
                    "Expected a JSON object for improvement response.");
        }

        requireType(root, "improvedSummary", JsonNode::isTextual);
        requireType(root, "bulletImprovements", JsonNode::isArray);
        requireType(root, "improvementExplanations", JsonNode::isArray);
        requireType(root, "actionableChanges", JsonNode::isArray);

        JsonNode bulletsNode = root.get("bulletImprovements");
        if (bulletsNode.size() > 25) {
            throw new GeminiResponseException(GeminiResponseException.Reason.INVALID_STRUCTURE,
                    "bulletImprovements exceeds maximum permitted items.");
        }
        for (JsonNode bulletItem : bulletsNode) {
            if (!bulletItem.isObject()) {
                throw new GeminiResponseException(GeminiResponseException.Reason.INVALID_STRUCTURE,
                        "bulletImprovements item must be an object.");
            }
            requireType(bulletItem, "section", JsonNode::isTextual);
            requireType(bulletItem, "original", JsonNode::isTextual);
            requireType(bulletItem, "improved", JsonNode::isTextual);
            requireType(bulletItem, "explanation", JsonNode::isTextual);
        }

        for (String listField : List.of("improvementExplanations", "actionableChanges")) {
            JsonNode arrayNode = root.get(listField);
            if (arrayNode.size() > 25) {
                throw new GeminiResponseException(GeminiResponseException.Reason.INVALID_STRUCTURE,
                        listField + " exceeds maximum permitted items.");
            }
            for (JsonNode item : arrayNode) {
                if (!item.isTextual()) {
                    throw new GeminiResponseException(GeminiResponseException.Reason.INVALID_STRUCTURE,
                            "Array field '" + listField + "' must contain only strings.");
                }
            }
        }

        final ResumeImprovementDTO dto;
        try {
            dto = objectMapper.treeToValue(root, ResumeImprovementDTO.class);
        } catch (Exception e) {
            throw new GeminiResponseException(GeminiResponseException.Reason.INVALID_STRUCTURE,
                    providerName + " response could not be mapped to the improvement contract.", e);
        }
        validateImprovementDTO(dto);
        return dto;
    }

    private static void requireType(JsonNode root, String field, Predicate<JsonNode> expectedType) {
        JsonNode value = root.get(field);
        if (value == null || value.isNull() || !expectedType.test(value)) {
            throw new GeminiResponseException(GeminiResponseException.Reason.INVALID_STRUCTURE,
                    "Required field '" + field + "' is missing or has the wrong type.");
        }
    }

    private static void validateAnalysisDTO(ResumeAnalysisDTO dto) {
        if (dto.score() < 0 || dto.score() > 100) {
            throw new AnalysisContractValidationException(
                    AnalysisContractValidationException.Reason.SCORE_OUT_OF_RANGE);
        }
        if (dto.jobMatchScore() != null && (dto.jobMatchScore() < 0 || dto.jobMatchScore() > 100)) {
            throw new AnalysisContractValidationException(
                    AnalysisContractValidationException.Reason.SCORE_OUT_OF_RANGE);
        }
        if (dto.summary().isBlank() || dto.atsCompatibility().isBlank()) {
            throw new AnalysisContractValidationException(
                    AnalysisContractValidationException.Reason.BLANK_REQUIRED_TEXT);
        }
        for (List<String> values : List.of(dto.strongestSkills(), dto.missingOrWeakSkills(),
                dto.strengths(), dto.weaknesses(), dto.suggestions(), dto.recommendedChanges())) {
            if (values.stream().anyMatch(value -> value == null || value.isBlank())) {
                throw new AnalysisContractValidationException(
                        AnalysisContractValidationException.Reason.BLANK_LIST_ITEM);
            }
        }
    }

    private static void validateImprovementDTO(ResumeImprovementDTO dto) {
        if (dto.improvedSummary() == null || dto.improvedSummary().isBlank()) {
            throw new AnalysisContractValidationException(
                    AnalysisContractValidationException.Reason.BLANK_REQUIRED_TEXT);
        }
        if (dto.bulletImprovements() == null || dto.bulletImprovements().isEmpty()) {
            throw new AnalysisContractValidationException(
                    AnalysisContractValidationException.Reason.BLANK_REQUIRED_TEXT);
        }
        for (BulletImprovementDTO item : dto.bulletImprovements()) {
            if (item == null
                    || item.section() == null || item.section().isBlank()
                    || item.original() == null || item.original().isBlank()
                    || item.improved() == null || item.improved().isBlank()
                    || item.explanation() == null || item.explanation().isBlank()) {
                throw new AnalysisContractValidationException(
                        AnalysisContractValidationException.Reason.BLANK_REQUIRED_TEXT);
            }
        }
        for (List<String> values : List.of(dto.improvementExplanations(), dto.actionableChanges())) {
            if (values == null || values.isEmpty()
                    || values.stream().anyMatch(value -> value == null || value.isBlank())) {
                throw new AnalysisContractValidationException(
                        AnalysisContractValidationException.Reason.BLANK_LIST_ITEM);
            }
        }
    }
}
