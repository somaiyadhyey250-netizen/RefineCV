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

    public static ResumeComparisonDTO parseAndValidateComparison(
            String rawResponse,
            String comparisonId,
            String fileNameA,
            String fileNameB,
            String jobDescription,
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

        record CategoryMeta(String id, String name, int maxPoints) {}
        List<CategoryMeta> expectedCategories = List.of(
                new CategoryMeta("content_relevance", "Content & Relevance", 20),
                new CategoryMeta("skills_keywords", "Skills & Keywords", 20),
                new CategoryMeta("experience_evidence", "Experience & Evidence", 20),
                new CategoryMeta("impact_achievements", "Impact & Achievements", 15),
                new CategoryMeta("clarity_structure", "Clarity & Structure", 15),
                new CategoryMeta("ats_compatibility", "ATS Compatibility", 10)
        );

        JsonNode catNode = root.get("categories");
        if (catNode == null || !catNode.isArray()) {
            throw new GeminiResponseException(GeminiResponseException.Reason.INVALID_STRUCTURE,
                    "Missing 'categories' array in comparison response.");
        }

        java.util.Map<String, JsonNode> catMap = new java.util.HashMap<>();
        for (JsonNode c : catNode) {
            String key = null;
            if (c.has("categoryId") && c.get("categoryId").isTextual()) {
                key = c.get("categoryId").asText().toLowerCase().trim();
            } else if (c.has("categoryName") && c.get("categoryName").isTextual()) {
                key = c.get("categoryName").asText().toLowerCase().replaceAll("[^a-z0-9]+", "_");
            } else if (c.has("name") && c.get("name").isTextual()) {
                key = c.get("name").asText().toLowerCase().replaceAll("[^a-z0-9]+", "_");
            }
            if (key != null) {
                catMap.put(key, c);
                if (key.contains("content")) catMap.put("content_relevance", c);
                if (key.contains("skill")) catMap.put("skills_keywords", c);
                if (key.contains("experience")) catMap.put("experience_evidence", c);
                if (key.contains("impact") || key.contains("achievement")) catMap.put("impact_achievements", c);
                if (key.contains("clarity") || key.contains("structure")) catMap.put("clarity_structure", c);
                if (key.contains("ats")) catMap.put("ats_compatibility", c);
            }
        }

        List<CategoryComparisonDTO> parsedCategories = new java.util.ArrayList<>();
        int totalA = 0;
        int totalB = 0;

        for (CategoryMeta meta : expectedCategories) {
            JsonNode item = catMap.get(meta.id());
            int scoreA = meta.maxPoints() / 2;
            int scoreB = meta.maxPoints() / 2;
            String evA = "Evidence evaluated from Candidate A.";
            String evB = "Evidence evaluated from Candidate B.";
            String expA = "Demonstrated proficiency in " + meta.name();
            String expB = "Demonstrated proficiency in " + meta.name();

            if (item != null) {
                if (item.has("scoreA") && item.get("scoreA").isNumber()) {
                    scoreA = Math.max(0, Math.min(item.get("scoreA").asInt(), meta.maxPoints()));
                }
                if (item.has("scoreB") && item.get("scoreB").isNumber()) {
                    scoreB = Math.max(0, Math.min(item.get("scoreB").asInt(), meta.maxPoints()));
                }
                if (item.has("evidenceA") && item.get("evidenceA").isTextual() && !item.get("evidenceA").asText().isBlank()) {
                    evA = item.get("evidenceA").asText().trim();
                }
                if (item.has("evidenceB") && item.get("evidenceB").isTextual() && !item.get("evidenceB").asText().isBlank()) {
                    evB = item.get("evidenceB").asText().trim();
                }
                if (item.has("explanationA") && item.get("explanationA").isTextual() && !item.get("explanationA").asText().isBlank()) {
                    expA = item.get("explanationA").asText().trim();
                }
                if (item.has("explanationB") && item.get("explanationB").isTextual() && !item.get("explanationB").asText().isBlank()) {
                    expB = item.get("explanationB").asText().trim();
                }
            }

            totalA += scoreA;
            totalB += scoreB;
            String winner = scoreA > scoreB ? "A" : (scoreB > scoreA ? "B" : "TIE");
            parsedCategories.add(new CategoryComparisonDTO(
                    meta.id(), meta.name(), meta.maxPoints(),
                    scoreA, scoreB, evA, evB, expA, expB, winner
            ));
        }

        int scoreDifference = Math.abs(totalA - totalB);
        String winner;
        String verdictTitle;
        String verdictExplanation;

        if (scoreDifference >= 8) {
            winner = totalA > totalB ? "A" : "B";
            verdictTitle = (totalA > totalB ? "Resume A" : "Resume B") + " is stronger overall";
            verdictExplanation = (totalA > totalB ? "Resume A" : "Resume B") + " demonstrates a substantial overall advantage across multiple core categories.";
        } else if (scoreDifference >= 4) {
            winner = totalA > totalB ? "A" : "B";
            verdictTitle = (totalA > totalB ? "Resume A" : "Resume B") + " has a narrow overall advantage";
            verdictExplanation = (totalA > totalB ? "Resume A" : "Resume B") + " maintains a slight edge, though both candidates demonstrate notable strengths.";
        } else {
            winner = "TIE";
            verdictTitle = "Too close to call";
            verdictExplanation = "Both resumes are closely matched across the evaluated categories with no decisive advantage.";
        }

        List<String> keyDifferentiators = new java.util.ArrayList<>();
        if (!"TIE".equals(winner)) {
            JsonNode diffsNode = root.has("keyDifferentiators") ? root.get("keyDifferentiators")
                    : (root.has("winnerDifferentiators") ? root.get("winnerDifferentiators") : null);
            if (diffsNode != null && diffsNode.isArray()) {
                for (JsonNode d : diffsNode) {
                    if (d.isTextual() && !d.asText().isBlank()) {
                        keyDifferentiators.add(d.asText().trim());
                    }
                }
            }
            if (keyDifferentiators.isEmpty()) {
                final String w = winner;
                List<CategoryComparisonDTO> wonCats = parsedCategories.stream()
                        .filter(c -> w.equals(c.winner()))
                        .sorted((c1, c2) -> Integer.compare(
                                Math.abs(c2.scoreA() - c2.scoreB()),
                                Math.abs(c1.scoreA() - c1.scoreB())
                        ))
                        .toList();
                for (CategoryComparisonDTO c : wonCats) {
                    if (keyDifferentiators.size() < 3) {
                        keyDifferentiators.add("Stronger evidence in " + c.name() + " (" + (w.equals("A") ? c.scoreA() : c.scoreB()) + "/" + c.maxPoints() + ")");
                    }
                }
            }
            while (keyDifferentiators.size() < 3) {
                keyDifferentiators.add("Demonstrated stronger overall qualifications and alignment");
            }
            if (keyDifferentiators.size() > 3) {
                keyDifferentiators = keyDifferentiators.subList(0, 3);
            }
        }

        String overallTakeaway = "Both resumes provide distinct professional backgrounds.";
        if (root.has("overallTakeaway") && root.get("overallTakeaway").isTextual() && !root.get("overallTakeaway").asText().isBlank()) {
            overallTakeaway = root.get("overallTakeaway").asText().trim();
        }

        List<String> borrowsA = new java.util.ArrayList<>();
        JsonNode arrA = root.has("resumeABorrowsFromB") ? root.get("resumeABorrowsFromB")
                : (root.has("borrowFromBForA") ? root.get("borrowFromBForA") : null);
        if (arrA != null && arrA.isArray()) {
            for (JsonNode n : arrA) {
                if (n.isTextual() && !n.asText().isBlank()) borrowsA.add(n.asText().trim());
            }
        }
        if (borrowsA.isEmpty()) {
            borrowsA.add("Adopt more quantified achievements and metric-driven bullet points.");
            borrowsA.add("Enhance standard section naming conventions to improve parsing clarity.");
        }

        List<String> borrowsB = new java.util.ArrayList<>();
        JsonNode arrB = root.has("resumeBBorrowsFromA") ? root.get("resumeBBorrowsFromA")
                : (root.has("borrowFromAForB") ? root.get("borrowFromAForB") : null);
        if (arrB != null && arrB.isArray()) {
            for (JsonNode n : arrB) {
                if (n.isTextual() && !n.asText().isBlank()) borrowsB.add(n.asText().trim());
            }
        }
        if (borrowsB.isEmpty()) {
            borrowsB.add("Incorporate stronger action verbs at the opening of each accomplishment.");
            borrowsB.add("Highlight domain competencies and technical skills more prominently.");
        }

        String jobContext = null;
        if (root.has("jobContext") && root.get("jobContext").isTextual() && !root.get("jobContext").asText().isBlank() && !"null".equalsIgnoreCase(root.get("jobContext").asText())) {
            jobContext = root.get("jobContext").asText().trim();
        } else if (jobDescription != null && !jobDescription.isBlank()) {
            jobContext = "Target Position";
        }

        return new ResumeComparisonDTO(
                comparisonId,
                fileNameA,
                fileNameB,
                totalA,
                totalB,
                scoreDifference,
                winner,
                verdictTitle,
                verdictExplanation,
                keyDifferentiators,
                parsedCategories,
                overallTakeaway,
                borrowsA,
                borrowsB,
                false,
                false,
                null,
                jobDescription,
                jobContext,
                null,
                null,
                providerName,
                java.time.Instant.now().toString()
        );
    }

    public static InterviewPrepDTO parseAndValidateInterviewPrep(String rawResponse, String prepId, String filename) {
        return parseAndValidateInterviewPrep(rawResponse, prepId, filename, 50000, new ObjectMapper(), "AI Provider");
    }

    public static List<InterviewQuestionDTO> parseAndValidateAdditionalQuestions(String rawResponse) {
        return parseAndValidateAdditionalQuestions(rawResponse, 50000, new ObjectMapper(), "AI Provider");
    }

    public static InterviewAnswerEvaluationDTO parseAndValidateAnswerEvaluation(String rawResponse, String questionId) {
        return parseAndValidateAnswerEvaluation(rawResponse, questionId, 50000, new ObjectMapper(), "AI Provider");
    }

    public static InterviewPrepDTO parseAndValidateInterviewPrep(
            String rawResponse,
            String prepId,
            String filename,
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
                    "Expected a JSON object for interview prep.");
        }

        if (!root.has("questions") || !root.get("questions").isArray()) {
            throw new GeminiResponseException(GeminiResponseException.Reason.INVALID_STRUCTURE,
                    "Field 'questions' must be a JSON array.");
        }

        List<InterviewQuestionDTO> questions = new java.util.ArrayList<>();
        for (JsonNode qNode : root.get("questions")) {
            if (!qNode.isObject()) continue;
            String question = qNode.has("question") && qNode.get("question").isTextual()
                    ? qNode.get("question").asText().trim() : null;
            if (question == null || question.isBlank()) continue;

            String qType = qNode.has("questionType") && qNode.get("questionType").isTextual()
                    ? qNode.get("questionType").asText().trim() : "DEPTH";
            String risk = qNode.has("riskLevel") && qNode.get("riskLevel").isTextual()
                    ? qNode.get("riskLevel").asText().trim() : "BE_READY";
            String basedOn = qNode.has("basedOn") && qNode.get("basedOn").isTextual()
                    ? qNode.get("basedOn").asText().trim() : "Resume Experience";
            String intent = qNode.has("interviewerIntent") && qNode.get("interviewerIntent").isTextual()
                    ? qNode.get("interviewerIntent").asText().trim() : "To evaluate depth of knowledge.";
            String hint = qNode.has("preparationHint") && qNode.get("preparationHint").isTextual()
                    ? qNode.get("preparationHint").asText().trim() : "Explain your role, decisions, and concrete outcomes.";

            questions.add(new InterviewQuestionDTO(
                    java.util.UUID.randomUUID().toString(),
                    question,
                    qType,
                    risk,
                    basedOn,
                    intent,
                    hint
            ));
        }

        if (questions.isEmpty()) {
            throw new GeminiResponseException(GeminiResponseException.Reason.INVALID_STRUCTURE,
                    "Interview prep response must contain at least one valid grounded question.");
        }

        List<InterviewClaimDTO> claims = new java.util.ArrayList<>();
        if (root.has("claimsToPrepare") && root.get("claimsToPrepare").isArray()) {
            for (JsonNode cNode : root.get("claimsToPrepare")) {
                if (!cNode.isObject()) continue;
                String claim = cNode.has("claim") && cNode.get("claim").isTextual()
                        ? cNode.get("claim").asText().trim() : null;
                if (claim == null || claim.isBlank()) continue;

                String risk = cNode.has("riskLevel") && cNode.get("riskLevel").isTextual()
                        ? cNode.get("riskLevel").asText().trim() : "BE_READY";
                String note = cNode.has("preparationNote") && cNode.get("preparationNote").isTextual()
                        ? cNode.get("preparationNote").asText().trim() : "Be ready to explain your exact contribution.";

                claims.add(new InterviewClaimDTO(
                        java.util.UUID.randomUUID().toString(),
                        claim,
                        risk,
                        note
                ));
            }
        }

        String overallNote = "Your resume contains several project and technology claims that are likely to invite follow-up questions. Focus your preparation on explaining your exact contribution, technical decisions, and measurable outcomes.";
        if (root.has("overallPreparationNote") && root.get("overallPreparationNote").isTextual()
                && !root.get("overallPreparationNote").asText().isBlank()) {
            overallNote = root.get("overallPreparationNote").asText().trim();
        }

        return new InterviewPrepDTO(
                prepId,
                filename,
                java.time.Instant.now().toString(),
                questions.size(),
                questions,
                claims,
                overallNote,
                false,
                null,
                providerName
        );
    }

    public static List<InterviewQuestionDTO> parseAndValidateAdditionalQuestions(
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

        if (root == null || !root.isObject() || !root.has("questions") || !root.get("questions").isArray()) {
            throw new GeminiResponseException(GeminiResponseException.Reason.INVALID_STRUCTURE,
                    "Expected a JSON object with 'questions' array.");
        }

        List<InterviewQuestionDTO> questions = new java.util.ArrayList<>();
        for (JsonNode qNode : root.get("questions")) {
            if (!qNode.isObject()) continue;
            String question = qNode.has("question") && qNode.get("question").isTextual()
                    ? qNode.get("question").asText().trim() : null;
            if (question == null || question.isBlank()) continue;

            String qType = qNode.has("questionType") && qNode.get("questionType").isTextual()
                    ? qNode.get("questionType").asText().trim() : "DEPTH";
            String risk = qNode.has("riskLevel") && qNode.get("riskLevel").isTextual()
                    ? qNode.get("riskLevel").asText().trim() : "BE_READY";
            String basedOn = qNode.has("basedOn") && qNode.get("basedOn").isTextual()
                    ? qNode.get("basedOn").asText().trim() : "Resume Experience";
            String intent = qNode.has("interviewerIntent") && qNode.get("interviewerIntent").isTextual()
                    ? qNode.get("interviewerIntent").asText().trim() : "To evaluate depth of knowledge.";
            String hint = qNode.has("preparationHint") && qNode.get("preparationHint").isTextual()
                    ? qNode.get("preparationHint").asText().trim() : "Explain your role, decisions, and concrete outcomes.";

            questions.add(new InterviewQuestionDTO(
                    java.util.UUID.randomUUID().toString(),
                    question,
                    qType,
                    risk,
                    basedOn,
                    intent,
                    hint
            ));
        }

        return questions;
    }

    public static InterviewAnswerEvaluationDTO parseAndValidateAnswerEvaluation(
            String rawResponse,
            String questionId,
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
                    "Expected a JSON object for answer evaluation.");
        }

        String answerQuality = root.has("answerQuality") && root.get("answerQuality").isTextual()
                ? root.get("answerQuality").asText().trim()
                : "Your answer provides relevant context. Consider emphasizing concrete outcomes and personal responsibilities.";

        List<String> strengths = new java.util.ArrayList<>();
        if (root.has("strengths") && root.get("strengths").isArray()) {
            for (JsonNode s : root.get("strengths")) {
                if (s.isTextual() && !s.asText().isBlank()) {
                    strengths.add(s.asText().trim());
                }
            }
        }
        if (strengths.isEmpty()) {
            strengths.add("Directly addresses the subject of the question.");
        }

        List<String> improvements = new java.util.ArrayList<>();
        if (root.has("improvements") && root.get("improvements").isArray()) {
            for (JsonNode imp : root.get("improvements")) {
                if (imp.isTextual() && !imp.asText().isBlank()) {
                    improvements.add(imp.asText().trim());
                }
            }
        }
        if (improvements.isEmpty()) {
            improvements.add("Include more specific metrics, challenges encountered, or architectural trade-offs.");
        }

        return new InterviewAnswerEvaluationDTO(
                questionId,
                answerQuality,
                strengths,
                improvements
        );
    }
}
