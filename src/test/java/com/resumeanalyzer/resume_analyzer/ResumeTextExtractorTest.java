package com.resumeanalyzer.resume_analyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class ResumeTextExtractorTest {

    private final ResumeTextExtractor extractor = extractorWithLimits(20, 50_000);

    @Test
    void extractsTextFromValidPdfWithinPageLimit() throws Exception {
        String text = extractor.extractText(pdfWithText("Java developer with project experience."));

        assertTrue(text.contains("Java developer"));
    }

    @Test
    void rejectsMalformedPdf() {
        ResumeValidationException exception = assertThrows(
                ResumeValidationException.class,
                () -> extractor.extractText("not a PDF".getBytes())
        );

        assertEquals(ResumeValidationException.Reason.INVALID_PDF, exception.getReason());
    }

    @Test
    void rejectsPdfAboveConfiguredPageLimitBeforeOcr() throws Exception {
        ResumeTextExtractor onePageLimit = extractorWithLimits(1, 50_000);

        ResumeValidationException exception = assertThrows(
                ResumeValidationException.class,
                () -> onePageLimit.extractText(pdfWithPages(2))
        );

        assertEquals(ResumeValidationException.Reason.TOO_MANY_PAGES, exception.getReason());
    }

    @Test
    void rejectsPdfWithNoMeaningfulExtractedOrOcrText() throws Exception {
        java.util.List<String> progress = new java.util.ArrayList<>();
        ResumeValidationException exception = assertThrows(
                ResumeValidationException.class,
                () -> extractor.extractText(pdfWithPages(0), progress::add)
        );

        assertEquals(ResumeValidationException.Reason.NO_READABLE_TEXT, exception.getReason());
        assertTrue(progress.contains("ocr"));
    }

    @Test
    void testBlankPageTriggersOcrExecution() throws Exception {
        java.util.List<String> progress = new java.util.ArrayList<>();
        ResumeValidationException exception = assertThrows(
                ResumeValidationException.class,
                () -> extractor.extractText(pdfWithPages(1), progress::add)
        );
        assertEquals(ResumeValidationException.Reason.NO_READABLE_TEXT, exception.getReason());
        assertTrue(progress.contains("ocr-page-1"));
        assertTrue(progress.contains("ocr-complete"));
    }

    @Test
    void rejectsOversizedPageDimensionBeforeOcrAndUsesSafeErrorCategory() throws Exception {
        java.util.List<String> progress = new java.util.ArrayList<>();
        ResumeValidationException exception = assertThrows(
                ResumeValidationException.class,
                () -> extractor.extractText(pdfWithPage(new PDRectangle(3601, 792)), progress::add)
        );

        assertEquals(ResumeValidationException.Reason.PAGE_DIMENSIONS_TOO_LARGE, exception.getReason());
        assertTrue(!progress.contains("ocr"));
        assertEquals("Please upload a valid, readable PDF resume.", AnalysisErrorMessages.forException(exception));
    }

    @Test
    void rejectsPageWhoseRenderedPixelAreaExceedsBudget() throws Exception {
        // Each side is below the point limits, but 3000 x 3000 points exceeds 8 million pixels at 200 DPI.
        ResumeValidationException exception = assertThrows(
                ResumeValidationException.class,
                () -> extractor.extractText(pdfWithPage(new PDRectangle(3000, 3000)))
        );

        assertEquals(ResumeValidationException.Reason.PAGE_DIMENSIONS_TOO_LARGE, exception.getReason());
    }

    @Test
    void configuredPageDimensionLimitIsRespected() throws Exception {
        ResumeTextExtractor stricterGeometryLimit = new ResumeTextExtractor(20, 50_000,
                500, 3600, 8_000_000, "tessdata");

        ResumeValidationException exception = assertThrows(
                ResumeValidationException.class,
                () -> stricterGeometryLimit.extractText(pdfWithText("Java developer."))
        );

        assertEquals(ResumeValidationException.Reason.PAGE_DIMENSIONS_TOO_LARGE, exception.getReason());
    }

    @Test
    void rejectsExtractedTextAboveConfiguredCharacterLimit() throws Exception {
        ResumeTextExtractor shortTextLimit = extractorWithLimits(20, 100);

        ResumeValidationException exception = assertThrows(
                ResumeValidationException.class,
                () -> shortTextLimit.extractText(pdfWithText("Resume content. ".repeat(15)))
        );

        assertEquals(ResumeValidationException.Reason.TEXT_TOO_LARGE, exception.getReason());
    }

    @Test
    void rejectsPasswordProtectedPdf() throws Exception {
        byte[] encryptedPdf;
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            StandardProtectionPolicy policy = new StandardProtectionPolicy(
                    "owner-password", "user-password", new AccessPermission()
            );
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            encryptedPdf = save(document);
        }

        ResumeValidationException exception = assertThrows(
                ResumeValidationException.class,
                () -> extractor.extractText(encryptedPdf)
        );

        assertEquals(ResumeValidationException.Reason.ENCRYPTED_PDF, exception.getReason());
    }

    @Test
    void tessdataPathIsOnlyRequiredWhenOcrIsNeeded() throws Exception {
        ResumeTextExtractor missingTessdata = new ResumeTextExtractor(20, 50_000,
                3600, 3600, 8_000_000, "missing-tessdata-directory");

        assertTrue(missingTessdata.extractText(pdfWithText("Java developer."))
                .contains("Java developer"));

        ResumeProcessingException exception = assertThrows(
                ResumeProcessingException.class,
                () -> missingTessdata.extractText(pdfWithPages(0))
        );
        assertEquals(ResumeProcessingException.Stage.OCR, exception.getStage());
        assertEquals("We couldn't process this resume. Please try again.",
                AnalysisErrorMessages.forException(exception));
    }

    @Test
    void defaultOcrMaxPagesIsThree() {
        assertEquals(3, extractor.getMaxOcrPages());
    }

    @Test
    void rejectsNonPositiveOcrMaxPages() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ResumeTextExtractor(20, 50_000, 3600, 3600, 8_000_000, "tessdata", 0)
        );
    }

    @Test
    void onePageScannedPdfPerformsOcrAndEmitsPageProgress() throws Exception {
        java.util.List<String> progress = new java.util.ArrayList<>();
        byte[] scannedPdf = scannedPdfWithTextPages("Experienced Java Engineer with Spring Boot and AWS.");
        String text = extractor.extractText(scannedPdf, progress::add);

        assertTrue(text.contains("Java") || text.contains("Engineer") || text.contains("Spring"));
        assertTrue(progress.contains("ocr"));
        assertTrue(progress.contains("ocr-page-1"));
        assertTrue(progress.contains("ocr-page-1-of-1"));
        assertTrue(!progress.contains("ocr-page-2"));
        assertTrue(progress.contains("ocr-complete"));
    }

    @Test
    void twoPageScannedPdfOcrsBothPagesAndEmitsPerpageProgress() throws Exception {
        java.util.List<String> progress = new java.util.ArrayList<>();
        byte[] scannedPdf = scannedPdfWithTextPages(
                "First page with Java and cloud backend skills.",
                "Second page with education and degree in engineering."
        );
        String text = extractor.extractText(scannedPdf, progress::add);

        assertTrue(text.contains("Java") || text.contains("skills"));
        assertTrue(progress.contains("ocr"));
        assertTrue(progress.contains("ocr-page-1"));
        assertTrue(progress.contains("ocr-page-1-of-2"));
        assertTrue(progress.contains("ocr-page-2"));
        assertTrue(progress.contains("ocr-page-2-of-2"));
        assertTrue(!progress.contains("ocr-page-3"));
        assertTrue(progress.contains("ocr-complete"));
    }

    @Test
    void threePageScannedPdfOcrsAllThreePages() throws Exception {
        java.util.List<String> progress = new java.util.ArrayList<>();
        byte[] scannedPdf = scannedPdfWithTextPages(
                "Page one summary of professional software development.",
                "Page two technical work history and microservices experience.",
                "Page three certifications and university academic honors."
        );
        String text = extractor.extractText(scannedPdf, progress::add);

        assertTrue(text.contains("Page") || text.contains("software") || text.contains("development"));
        assertTrue(progress.contains("ocr-page-1"));
        assertTrue(progress.contains("ocr-page-1-of-3"));
        assertTrue(progress.contains("ocr-page-2"));
        assertTrue(progress.contains("ocr-page-2-of-3"));
        assertTrue(progress.contains("ocr-page-3"));
        assertTrue(progress.contains("ocr-page-3-of-3"));
        assertTrue(!progress.contains("ocr-page-4"));
        assertTrue(progress.contains("ocr-complete"));
    }

    @Test
    void fourOrMorePageScannedPdfStopsOcrAtConfiguredLimit() throws Exception {
        java.util.List<String> progress = new java.util.ArrayList<>();
        // Extractor has default maxOcrPages = 3, but the PDF has 4 pages
        byte[] scannedPdf = scannedPdfWithTextPages(
                "Page 1 developer profile.",
                "Page 2 project details.",
                "Page 3 leadership experience.",
                "Page 4 additional publications."
        );
        String text = extractor.extractText(scannedPdf, progress::add);

        assertTrue(progress.contains("ocr-page-1"));
        assertTrue(progress.contains("ocr-page-1-of-3"));
        assertTrue(progress.contains("ocr-page-2"));
        assertTrue(progress.contains("ocr-page-2-of-3"));
        assertTrue(progress.contains("ocr-page-3"));
        assertTrue(progress.contains("ocr-page-3-of-3"));
        // Page 4 must NOT be processed by OCR
        assertTrue(!progress.contains("ocr-page-4"));
        assertTrue(progress.contains("ocr-complete"));
    }

    @Test
    void exportVerificationPdfs() throws Exception {
        java.nio.file.Path dir = java.nio.file.Paths.get("target", "test-pdfs");
        java.nio.file.Files.createDirectories(dir);

        java.nio.file.Files.write(dir.resolve("text_resume.pdf"),
                pdfWithText("Alice Smith - Software Engineer with 5 years experience in Java Spring Boot Microservices AWS Docker Kubernetes SQL and Git."));

        java.nio.file.Files.write(dir.resolve("scanned_1p.pdf"),
                scannedPdfWithTextPages("Alice Smith - Senior Java Developer\nSkills: Java, Spring Boot, PostgreSQL, Docker, AWS.\nExperience: 5+ years building scalable distributed backends."));

        java.nio.file.Files.write(dir.resolve("scanned_2p.pdf"),
                scannedPdfWithTextPages(
                        "Alice Smith - Senior Java Developer\nSkills: Java, Spring Boot, PostgreSQL, Docker, AWS.\nExperience: 5+ years building scalable distributed backends.",
                        "Education: B.S. in Computer Science.\nCertifications: AWS Certified Solutions Architect, Oracle Java SE 11 Developer."
                ));

        java.nio.file.Files.write(dir.resolve("scanned_3p.pdf"),
                scannedPdfWithTextPages(
                        "Alice Smith - Senior Java Developer\nSkills: Java, Spring Boot, PostgreSQL, Docker, AWS.\nExperience: 5+ years building scalable distributed backends.",
                        "Projects: Built high throughput microservice gateway processing 50k req/s.\nAutomated CI/CD pipelines with GitHub Actions.",
                        "Education: B.S. in Computer Science.\nCertifications: AWS Certified Solutions Architect, Oracle Java SE 11 Developer."
                ));
    }

    @Test
    void ocrRespectsConfiguredPageLimit() throws Exception {
        ResumeTextExtractor customLimitExtractor = new ResumeTextExtractor(
                20, 50_000, 3600, 3600, 8_000_000, "tessdata", 1
        );
        assertEquals(1, customLimitExtractor.getMaxOcrPages());

        java.util.List<String> progress = new java.util.ArrayList<>();
        byte[] scannedPdf = scannedPdfWithTextPages(
                "Page 1 should be processed.",
                "Page 2 should be skipped by configured limit."
        );
        String text = customLimitExtractor.extractText(scannedPdf, progress::add);

        assertTrue(progress.contains("ocr-page-1"));
        assertTrue(!progress.contains("ocr-page-2"));
        assertTrue(progress.contains("ocr-complete"));
    }

    private byte[] scannedPdfWithTextPages(String... pageTexts) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (String pageText : pageTexts) {
                int width = (int) (8.5 * 100);
                int height = (int) (11.0 * 100);
                java.awt.image.BufferedImage bi = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g2d = bi.createGraphics();
                g2d.setColor(java.awt.Color.WHITE);
                g2d.fillRect(0, 0, width, height);
                g2d.setColor(java.awt.Color.BLACK);
                g2d.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 24));
                g2d.drawString(pageText, 40, 100);
                g2d.dispose();

                PDPage page = new PDPage(PDRectangle.LETTER);
                document.addPage(page);

                var pdImage = org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(document, bi);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.drawImage(pdImage, 0, 0, PDRectangle.LETTER.getWidth(), PDRectangle.LETTER.getHeight());
                }
                bi.flush();
            }
            return save(document);
        }
    }

    private byte[] pdfWithPages(int pageCount) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pageCount; i++) {
                document.addPage(new PDPage());
            }
            return save(document);
        }
    }

    private byte[] pdfWithPage(PDRectangle pageSize) throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(pageSize));
            return save(document);
        }
    }

    private byte[] pdfWithText(String text) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText(text);
                content.endText();
            }
            return save(document);
        }
    }

    private byte[] save(PDDocument document) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        document.save(output);
        return output.toByteArray();
    }

    private static ResumeTextExtractor extractorWithLimits(int maxPages, int maxCharacters) {
        return new ResumeTextExtractor(maxPages, maxCharacters, 3600, 3600, 8_000_000, "tessdata");
    }
}
