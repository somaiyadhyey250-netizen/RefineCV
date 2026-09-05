package com.resumeanalyzer.resume_analyzer;

import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

@Service
public class ResumeTextExtractor {

    public String extractText(byte[] pdfBytes) throws IOException {

        // First try normal PDF text extraction
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {

            PDFTextStripper stripper =
                    new PDFTextStripper();

            String text =
                    stripper.getText(document);

            // If PDF already contains text, return it
            if (text != null && !text.trim().isEmpty()) {
                return text;
            }

            // Otherwise use OCR
            return extractUsingOCR(document);
        }
    }


    private String extractUsingOCR(PDDocument document) {

        Tesseract tesseract = new Tesseract();

        // Location of tessdata folder
        tesseract.setDatapath("tessdata");

        // English language
        tesseract.setLanguage("eng");

        PDFRenderer renderer =
                new PDFRenderer(document);

        StringBuilder extractedText =
                new StringBuilder();


        try {

            for (
                int page = 0;
                page < document.getNumberOfPages();
                page++
            ) {

                // Render PDF page at 200 DPI
                // Faster than 300 DPI while still
                // providing good OCR quality for resumes.
                var image =
                        renderer.renderImageWithDPI(
                                page,
                                200
                        );


                // Run OCR
                String pageText =
                        tesseract.doOCR(image);


                extractedText.append(pageText);
                extractedText.append("\n");

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