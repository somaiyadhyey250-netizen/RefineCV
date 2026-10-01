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
