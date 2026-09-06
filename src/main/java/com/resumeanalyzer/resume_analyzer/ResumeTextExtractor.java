package com.resumeanalyzer.resume_analyzer;

import java.io.IOException;
import java.util.function.Consumer;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

@Service
public class ResumeTextExtractor {

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


        try (
            PDDocument document =
                    Loader.loadPDF(pdfBytes)
        ) {


            PDFTextStripper stripper =
                    new PDFTextStripper();


            String text =
                    stripper.getText(document);


            /*
             * PDFBox successfully found text.
             *
             * OCR is not required.
             */

            if (
                text != null &&
                !text.trim().isEmpty()
            ) {

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


            return ocrText;

        }
    }


    /*
     * OCR extraction using Tesseract.
     */
    private String extractUsingOCR(
            PDDocument document
    ) {


        Tesseract tesseract =
                new Tesseract();


        /*
         * Location of tessdata folder.
         *
         * The project contains:
         *
         * tessdata/
         *     eng.traineddata
         */

        tesseract.setDatapath(
                "tessdata"
        );


        // English language
        tesseract.setLanguage(
                "eng"
        );


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

                String pageText =
                        tesseract.doOCR(
                                image
                        );


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


            throw new RuntimeException(
                    "OCR failed while reading resume.",
                    e
            );

        }

    }

}