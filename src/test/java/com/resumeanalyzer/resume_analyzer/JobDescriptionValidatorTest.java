package com.resumeanalyzer.resume_analyzer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JobDescriptionValidatorTest {

    @Test
    @DisplayName("null or empty is rejected")
    void nullOrEmptyRejected() {
        assertFalse(JobDescriptionValidator.isValid(null));
        assertFalse(JobDescriptionValidator.isValid(""));
        assertFalse(JobDescriptionValidator.isValid("   \n\t  "));
    }

    @Test
    @DisplayName("Single character or no-letter input is rejected")
    void insufficientLettersRejected() {
        assertFalse(JobDescriptionValidator.isValid("a"));
        assertFalse(JobDescriptionValidator.isValid("1"));
        assertFalse(JobDescriptionValidator.isValid(" "));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "!@#$%^&*()_+ !@#$%^&*()_+ !@#$%^&*()_+",
            "12345 67890 12345 67890 12345 67890",
            ".... .... .... .... .... .... .... ....",
            "1234567890",
            "!@#$%^&*()_+",
            "??? !!! ::: ;;; /// \\\\",
            "!@#$%^&*()_+!@#$%^&*()_+ a"
    })
    @DisplayName("Symbol and number spam without meaningful letters is rejected")
    void symbolAndNumberSpamRejected(String spam) {
        assertFalse(JobDescriptionValidator.isValid(spam));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "developer developer developer developer",
            "test test test test test",
            "one two one two one two one two",
            "aaaaaaaaaaaaaaaaaaaa",
            "abababababababab",
            "wordOne wordTwo wordThree wordOne wordTwo wordThree wordOne wordTwo wordThree"
    })
    @DisplayName("Extreme repetitive junk is rejected")
    void extremeRepetitiveJunkRejected(String junk) {
        assertFalse(JobDescriptionValidator.isValid(junk));
    }

    @Test
    @DisplayName("Overly long JD (>8000 chars) is rejected")
    void overlyLongJdRejected() {
        String base = "Senior Software Engineer with Java and Spring Boot experience required. ";
        String longJd = base.repeat(200);
        assertTrue(longJd.length() > 8000);
        assertFalse(JobDescriptionValidator.isValid(longJd));
    }

    @Test
    @DisplayName("Exactly 8000 chars of valid JD is accepted")
    void exactlyMaxCharsAccepted() {
        String base = "Senior Software Engineer with Java and Spring Boot experience required. Responsibilities include building microservices and cloud APIs. Qualifications: Degree in Computer Science and 3+ years experience. ";
        String valid8000 = base.repeat(100).substring(0, 8000);
        assertTrue(JobDescriptionValidator.isValid(valid8000));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "banking sector",
            "Frontend Developer",
            "software developer",
            "marketing manager",
            "hospital receptionist",
            "QA",
            "HR",
            "Dev",
            "Java Developer",
            "Software Engineer, Java required"
    })
    @DisplayName("Meaningful short inputs, roles, and domain phrases are accepted")
    void meaningfulShortInputsAccepted(String input) {
        assertTrue(JobDescriptionValidator.isValid(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Artisan espresso bar seeks morning barista to pull shots, calibrate grinders, and steam microfoam milk.",
            "Residential framing carpenter needed to read blueprints, erect wall framing, and install roof trusses.",
            "Pediatric clinic needs evening triage nurse to administer vaccinations, chart vital signs, and counsel parents.",
            "Boutique litigation firm seeks paralegal to draft deposition summaries, index trial exhibits, and file court pleadings.",
            "Montessori preschool seeks lead guide to nurture toddler independence, observe child milestones, and lead circle time.",
            "French bistro seeking pastry chef to bake morning croissants, sourdough loaves, and delicate tarts from scratch.",
            "Need an experienced dog walker available weekday afternoons.",
            "High school physics tutor needed for Newtonian mechanics, lab experiments, and exam preparation.",
            "Commercial helicopter pilot for aerial surveying, crop dusting, and search-and-rescue flights.",
            "Orchestral cellist needed for upcoming symphony season. Repertoire includes Mahler, Brahms, and contemporary commissions."
    })
    @DisplayName("Legitimate non-technical or unusual job descriptions are accepted")
    void legitimateUnusualJobDescriptionsAccepted(String jd) {
        assertTrue(JobDescriptionValidator.isValid(jd));
    }

    @Test
    @DisplayName("Full realistic job description with skills and responsibilities is accepted")
    void fullRealisticJdAccepted() {
        String jd = """
                Job Title: Senior Backend Engineer
                Company: Tech Innovations Inc.
                Location: Remote (US)
                
                About the Role:
                We are looking for an experienced Senior Backend Engineer to join our core platform team.
                
                Responsibilities:
                - Architect, design, and implement scalable microservices using Java and Spring Boot.
                - Collaborate with cross-functional teams including frontend engineers and product managers.
                - Optimize database queries on PostgreSQL and Redis for low-latency responses.
                - Maintain CI/CD pipelines and cloud deployments on AWS.
                
                Qualifications:
                - Bachelor's degree in Computer Science or related field.
                - 4+ years of professional experience in backend development.
                - Strong proficiency with Java, REST APIs, and relational databases.
                - Experience with Docker, Kubernetes, and agile methodologies is preferred.
                """;
        assertTrue(JobDescriptionValidator.isValid(jd));
    }
}
