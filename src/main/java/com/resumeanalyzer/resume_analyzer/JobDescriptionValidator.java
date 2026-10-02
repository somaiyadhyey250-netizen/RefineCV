package com.resumeanalyzer.resume_analyzer;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates that user input provided as a target job description or role context
 * is usable text rather than empty, oversized, symbol spam, number spam, or extreme repetitive junk.
 *
 * Does not restrict meaningful concise role titles or domain phrases (e.g., "banking sector",
 * "Frontend Developer", "marketing manager"). Qualitative and specificity limitations are handled
 * downstream by the AI evaluator per strict prompt instructions.
 */
public final class JobDescriptionValidator {

    public static final int MAX_LENGTH = 8000;
    public static final int MIN_LETTERS = 2;

    private static final Pattern WORD_PATTERN = Pattern.compile("[a-zA-Z0-9+#.-]+");

    private JobDescriptionValidator() {
    }

    public static boolean isValid(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_LENGTH) {
            return false;
        }

        int letterCount = 0;
        Set<Character> distinctChars = new HashSet<>();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (Character.isLetter(c)) {
                letterCount++;
            }
            if (!Character.isWhitespace(c)) {
                distinctChars.add(Character.toLowerCase(c));
            }
        }

        // Reject obvious symbol spam and pure number spam (must have at least MIN_LETTERS)
        if (letterCount < MIN_LETTERS) {
            return false;
        }

        // Reject symbol-dominated spam for strings of moderate length
        if (trimmed.length() >= 10 && ((double) letterCount / trimmed.length()) < 0.25) {
            return false;
        }

        // Extreme character-level repetitive junk (e.g., "aaaaaaaaaaaa", "abababababab")
        if (trimmed.length() >= 8 && distinctChars.size() <= 2) {
            return false;
        }

        // Tokenize words containing letters
        Matcher matcher = WORD_PATTERN.matcher(trimmed.toLowerCase(Locale.ROOT));
        int wordCount = 0;
        Set<String> uniqueWords = new HashSet<>();

        while (matcher.find()) {
            String word = matcher.group();
            if (containsLetter(word)) {
                wordCount++;
                uniqueWords.add(word);
            }
        }

        if (wordCount == 0) {
            return false;
        }

        // Extreme word-level repetitive junk:
        // e.g. "test test test", "developer developer developer developer"
        if (wordCount >= 3 && uniqueWords.size() == 1) {
            return false;
        }
        // e.g. "one two one two one two one two"
        if (wordCount >= 6 && uniqueWords.size() <= 2) {
            return false;
        }
        // e.g. "wordOne wordTwo wordThree wordOne wordTwo wordThree wordOne..."
        if (wordCount >= 9 && uniqueWords.size() <= 3) {
            return false;
        }

        return true;
    }

    private static boolean containsLetter(String word) {
        for (int i = 0; i < word.length(); i++) {
            if (Character.isLetter(word.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
