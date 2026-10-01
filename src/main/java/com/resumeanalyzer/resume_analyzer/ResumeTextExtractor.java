package com.resumeanalyzer.resume_analyzer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Value;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

@Service
public class ResumeTextExtractor {

    private final int maxPdfPages;
    private final int maxExtractedTextCharacters;
    private final double maxOcrPageWidthPoints;
    private final double maxOcrPageHeightPoints;
    private final long maxOcrRenderedPixels;
    private final String tessdataPath;

    public ResumeTextExtractor(
            @Value("${refinecv.pdf.max-pages}") int maxPdfPages,
            @Value("${refinecv.pdf.max-extracted-text-characters}") int maxExtractedTextCharacters,
            @Value("${refinecv.pdf.ocr.max-page-width-points:3600}") double maxOcrPageWidthPoints,
            @Value("${refinecv.pdf.ocr.max-page-height-points:3600}") double maxOcrPageHeightPoints,
            @Value("${refinecv.pdf.ocr.max-rendered-pixels:8000000}") long maxOcrRenderedPixels,
            @Value("${refinecv.ocr.tessdata-path:tessdata}") String tessdataPath
    ) {
        if (maxPdfPages < 1 || maxExtractedTextCharacters < 1
                || maxOcrPageWidthPoints <= 0 || maxOcrPageHeightPoints <= 0
                || maxOcrRenderedPixels < 1) {
            throw new IllegalArgumentException("PDF extraction limits must be positive.");
        }
        this.maxPdfPages = maxPdfPages;
        this.maxExtractedTextCharacters = maxExtractedTextCharacters;
        this.maxOcrPageWidthPoints = maxOcrPageWidthPoints;
        this.maxOcrPageHeightPoints = maxOcrPageHeightPoints;
        this.maxOcrRenderedPixels = maxOcrRenderedPixels;
        this.tessdataPath = tessdataPath;
    }

    /*
     * Old method.
     *
     * This keeps the extractor compatible with existing code.
     * It simply calls the new method without sending progress updates.
     */
    public String extractText(byte[] pdfBytes) throws IOException {

        return extractText(
                pdfBytes,
                status -> {
                    // No progress listener required here.
                }
        );
    }


    /*
     * New method with progress reporting.
     *
     * Consumer<String> allows the caller
     * to receive updates while extraction is happening.
     */
    public String extractText(
            byte[] pdfBytes,
            Consumer<String> progress
    ) throws IOException {


        // =========================================
        // STEP 1 — EXTRACTING
        // =========================================

        progress.accept("extracting");


        final PDDocument loadedDocument;
        try {
            loadedDocument = Loader.loadPDF(pdfBytes);
        } catch (InvalidPasswordException e) {
            throw new ResumeValidationException(ResumeValidationException.Reason.ENCRYPTED_PDF, e);
        } catch (IOException e) {
            throw new ResumeValidationException(ResumeValidationException.Reason.INVALID_PDF, e);
        }

        try (PDDocument document = loadedDocument) {

            if (document.isEncrypted()) {
                throw new ResumeValidationException(ResumeValidationException.Reason.ENCRYPTED_PDF);
            }

            if (document.getNumberOfPages() > maxPdfPages) {
                throw new ResumeValidationException(ResumeValidationException.Reason.TOO_MANY_PAGES);
            }

            validateOcrPageGeometry(document);


            PDFTextStripper stripper =
                    new PDFTextStripper();


            String text;
            try {
                text = stripper.getText(document);
            } catch (IOException e) {
                throw new ResumeProcessingException(ResumeProcessingException.Stage.PDF_EXTRACTION, e);
            }


            /*
             * PDFBox successfully found text.
             *
             * OCR is not required.
             */

            if (
                text != null &&
                hasMeaningfulText(text)
            ) {

                ensureTextWithinLimit(text.length());

                progress.accept("extracted");

                return text;
            }


            /*
             * PDF contains little/no readable text.
             *
             * This usually means it is an
             * image-based/scanned resume.
             */

            progress.accept("extracted");


            // =========================================
            // STEP 2 — OCR
            // =========================================

            progress.accept("ocr");


            String ocrText =
                    extractUsingOCR(
                            document
                    );


            progress.accept("ocr-complete");

            if (!hasMeaningfulText(ocrText)) {
                throw new ResumeValidationException(ResumeValidationException.Reason.NO_READABLE_TEXT);
            }

            ensureTextWithinLimit(ocrText.length());


            return ocrText;

        }
    }


    /*
     * OCR extraction using Tesseract.
     */
    private String extractUsingOCR(
            PDDocument document
    ) {


        Tesseract tesseract = createTesseract();


        PDFRenderer renderer =
                new PDFRenderer(
                        document
                );


        StringBuilder extractedText =
                new StringBuilder();


        try {


            /*
             * Process every page of the PDF.
             */

            for (
                int page = 0;
                page < document.getNumberOfPages();
                page++
            ) {


                /*
                 * Render the PDF page as an image.
                 *
                 * 200 DPI gives a good balance
                 * between OCR quality and speed.
                 */

                var image =
                        renderer.renderImageWithDPI(
                                page,
                                200
                        );


                /*
                 * Run Tesseract OCR.
                 */

                String pageText;
                try {
                    pageText = tesseract.doOCR(image);
                } catch (RuntimeException | LinkageError e) {
                    throw new ResumeProcessingException(ResumeProcessingException.Stage.OCR,
                            new IOException("OCR engine could not process the page.", e));
                } finally {
                    image.flush();
                }

                if ((long) extractedText.length() + pageText.length() > maxExtractedTextCharacters) {
                    throw new ResumeValidationException(ResumeValidationException.Reason.TEXT_TOO_LARGE);
                }


                extractedText.append(
                        pageText
                );


                extractedText.append(
                        "\n"
                );

            }


            return extractedText.toString();


        } catch (
                IOException |
                TesseractException e
        ) {


            throw new ResumeProcessingException(ResumeProcessingException.Stage.OCR, e);

        }

    }

    private Tesseract createTesseract() {
        Path dataDirectory = resolveTessdataDirectory();
        try {
            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath(dataDirectory.toString());
            tesseract.setLanguage("eng");
            return tesseract;
        } catch (RuntimeException | LinkageError e) {
            throw new ResumeProcessingException(ResumeProcessingException.Stage.OCR,
                    new IOException("OCR engine could not be initialized.", e));
        }
    }

    private Path resolveTessdataDirectory() {
        try {
            if (tessdataPath == null || tessdataPath.isBlank()) {
                throw new IOException("OCR language data is unavailable.");
            }
            Path configuredPath = Path.of(tessdataPath);
            Path dataDirectory = (configuredPath.isAbsolute()
                    ? configuredPath
                    : Path.of("").toAbsolutePath().resolve(configuredPath)).normalize();
            Path languageData = dataDirectory.resolve("eng.traineddata");
            if (!Files.isDirectory(dataDirectory) || !Files.isReadable(dataDirectory)
                    || !Files.isRegularFile(languageData) || !Files.isReadable(languageData)) {
                throw new IOException("OCR language data is unavailable.");
            }
            return dataDirectory;
        } catch (IOException | InvalidPathException | SecurityException e) {
            throw new ResumeProcessingException(ResumeProcessingException.Stage.OCR,
                    new IOException("OCR language data is unavailable."));
        }
    }

    /** Reject page geometry that could allocate an excessive image at the OCR renderer's 200 DPI. */
    private void validateOcrPageGeometry(PDDocument document) {
        for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
            PDPage page = document.getPage(pageIndex);
            validatePageBox(page.getMediaBox());
            validatePageBox(page.getCropBox());
        }
    }

    private void validatePageBox(PDRectangle box) {
        double widthPoints = box.getWidth();
        double heightPoints = box.getHeight();
        if (!Double.isFinite(widthPoints) || !Double.isFinite(heightPoints)
                || widthPoints <= 0 || heightPoints <= 0
                || widthPoints > maxOcrPageWidthPoints
                || heightPoints > maxOcrPageHeightPoints) {
            throw new ResumeValidationException(ResumeValidationException.Reason.PAGE_DIMENSIONS_TOO_LARGE);
        }

        long widthPixels = (long) Math.ceil(widthPoints * 200.0 / 72.0);
        long heightPixels = (long) Math.ceil(heightPoints * 200.0 / 72.0);
        if (widthPixels > Integer.MAX_VALUE || heightPixels > Integer.MAX_VALUE
                || widthPixels * heightPixels > maxOcrRenderedPixels) {
            throw new ResumeValidationException(ResumeValidationException.Reason.PAGE_DIMENSIONS_TOO_LARGE);
        }
    }

    private boolean hasMeaningfulText(String text) {
        return text != null && text.codePoints().anyMatch(Character::isLetterOrDigit);
    }

    private void ensureTextWithinLimit(int characterCount) {
        if (characterCount > maxExtractedTextCharacters) {
            throw new ResumeValidationException(ResumeValidationException.Reason.TEXT_TOO_LARGE);
        }
    }

}
