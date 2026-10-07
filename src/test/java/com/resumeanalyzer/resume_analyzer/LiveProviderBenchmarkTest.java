package com.resumeanalyzer.resume_analyzer;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LiveProviderBenchmarkTest {

    private static final Logger logger = LoggerFactory.getLogger(LiveProviderBenchmarkTest.class);

    private static final String SAMPLE_RESUME_A = """
            Alex Mercer
            Senior Software Engineer | Java, Spring Boot, Microservices, Cloud
            Email: alex.mercer@example.com | Phone: (555) 019-2834 | San Francisco, CA

            SUMMARY
            Results-driven Senior Software Engineer with 7+ years of experience designing, building, and scaling
            high-throughput distributed systems in Java and Spring Boot. Proven track record of reducing system
            latency by 42% and leading cloud migrations to AWS and Kubernetes.

            EXPERIENCE
            Senior Backend Engineer | CloudScale Inc. | 2021 – Present
            - Architected microservices platform handling 15M daily requests using Spring Boot, Kafka, and Redis.
            - Reduced API p99 latency from 320ms to 85ms through connection pooling and caching strategies.
            - Led a team of 5 engineers delivering core payment gateway integration on time and under budget.

            Software Engineer | Apex FinTech | 2018 – 2021
            - Developed RESTful APIs and asynchronous event processing pipelines using Java 11 and PostgreSQL.
            - Automated CI/CD pipelines reducing deployment failure rates by 35%.

            SKILLS
            Languages: Java, Python, SQL, Bash
            Frameworks: Spring Boot, Spring Cloud, Hibernate, JUnit, Mockito
            Infrastructure: Docker, Kubernetes, AWS, PostgreSQL, Kafka, Redis

            EDUCATION
            B.S. in Computer Science | University of California, Berkeley | 2018
            """;

    private static final String SAMPLE_RESUME_B = """
            Jordan Rivera
            Full Stack & Backend Developer | Java, TypeScript, React, Docker
            Email: jordan.rivera@example.com | New York, NY

            SUMMARY
            Full Stack Software Engineer with 4 years of experience building modern web applications.
            Skilled in Java, Spring Boot backend development, and React frontends. Passionate about clean architecture.

            EXPERIENCE
            Full Stack Developer | TechPulse Solutions | 2021 – Present
            - Built full-stack features using React and Java Spring Boot REST endpoints.
            - Optimized SQL queries and reduced database query response time by 25%.
            - Collaborated with product designers to implement responsive, accessible UI components.

            Junior Java Developer | CoreByte Labs | 2020 – 2021
            - Maintained legacy Java backend services and resolved 150+ customer-reported defects.
            - Wrote comprehensive unit tests increasing overall code coverage from 62% to 84%.

            SKILLS
            Java, Spring Boot, JavaScript, TypeScript, React, PostgreSQL, Docker, Git

            EDUCATION
            B.S. in Software Engineering | Rochester Institute of Technology | 2020
            """;

    private static final String SAMPLE_JOB_DESC = """
            Senior Java Backend Engineer
            Requirements:
            - 5+ years of production experience in Java and Spring Boot microservices
            - Experience with high-throughput event-driven architectures (Kafka)
            - Strong knowledge of database optimization, distributed caching (Redis), and AWS/Kubernetes
            - Experience mentoring junior engineers and conducting architectural reviews
            """;

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_LIVE_BENCHMARK", matches = "true")
    void runBenchmark() {
        String geminiKey = System.getenv("GEMINI_API_KEY");
        String groqKey = System.getenv("GROQ_API_KEY");

        System.out.println("======================================================================");
        System.out.println("LIVE AI PROVIDER BENCHMARK (GEMINI VS GROQ)");
        System.out.println("======================================================================");

        if (groqKey != null && !groqKey.isBlank()) {
            GroqAIProvider groq = new GroqAIProvider(
                    groqKey, "openai/gpt-oss-20b", "https://api.groq.com/openai/v1",
                    Duration.ofSeconds(30), 20000
            );
            System.out.println("\n--- BENCHMARKING GROQ (openai/gpt-oss-20b) ---");
            benchmarkProvider("Groq", groq);
        } else {
            System.out.println("GROQ_API_KEY is not set; skipping Groq benchmark.");
        }

        if (geminiKey != null && !geminiKey.isBlank()) {
            GeminiService gemini = new GeminiService(20000, geminiKey, "gemini-3.6-flash");
            System.out.println("\n--- BENCHMARKING GEMINI (gemini-3.6-flash) ---");
            benchmarkProvider("Gemini", gemini);
        } else {
            System.out.println("GEMINI_API_KEY is not set; skipping Gemini benchmark.");
        }
    }

    private void benchmarkProvider(String name, AIProvider provider) {
        // 1. General Analysis
        try {
            long start = System.currentTimeMillis();
            ResumeAnalysisDTO analysis = provider.analyzeResume(SAMPLE_RESUME_A, status -> {});
            long duration = System.currentTimeMillis() - start;
            System.out.printf("[%s] 1. General Analysis: SUCCESS in %d ms (Score: %d)%n",
                    name, duration, analysis.score());

            // 2. Specific Job Analysis
            start = System.currentTimeMillis();
            ResumeAnalysisDTO jobAnalysis = provider.analyzeResumeForJob(SAMPLE_RESUME_A, SAMPLE_JOB_DESC, status -> {});
            duration = System.currentTimeMillis() - start;
            System.out.printf("[%s] 2. Specific Job Analysis: SUCCESS in %d ms (Job Match Score: %s)%n",
                    name, duration, jobAnalysis.jobMatchScore());

            // 3. Improvement
            start = System.currentTimeMillis();
            ResumeImprovementDTO improvement = provider.improveResume(SAMPLE_RESUME_A, analysis);
            duration = System.currentTimeMillis() - start;
            System.out.printf("[%s] 3. Improvement: SUCCESS in %d ms (Bullets improved: %d)%n",
                    name, duration, improvement.bulletImprovements() != null ? improvement.bulletImprovements().size() : 0);

            // 4. Comparison
            start = System.currentTimeMillis();
            ResumeComparisonDTO comparison = provider.compareResumes(
                    SAMPLE_RESUME_A, SAMPLE_RESUME_B, SAMPLE_JOB_DESC,
                    "cmp-bench-1", "Alex_Mercer.pdf", "Jordan_Rivera.pdf", status -> {}
            );
            duration = System.currentTimeMillis() - start;
            System.out.printf("[%s] 4. Comparison: SUCCESS in %d ms (Winner: %s, ScoreA: %d, ScoreB: %d)%n",
                    name, duration, comparison.winner(), comparison.totalScoreA(), comparison.totalScoreB());

        } catch (Exception e) {
            System.out.printf("[%s] BENCHMARK FAILED with %s: %s%n", name, e.getClass().getSimpleName(), e.getMessage());
            if (e.getCause() != null) {
                System.out.printf("[%s] Caused by: %s%n", name, e.getCause().getMessage());
            }
        }
    }
}
