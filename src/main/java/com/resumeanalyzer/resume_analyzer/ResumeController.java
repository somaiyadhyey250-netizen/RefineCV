package com.resumeanalyzer.resume_analyzer;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class ResumeController {

    private final ResumeTextExtractor resumeTextExtractor;
    private final GeminiService geminiService;

    public ResumeController(
            ResumeTextExtractor resumeTextExtractor,
            GeminiService geminiService) {

        this.resumeTextExtractor = resumeTextExtractor;
        this.geminiService = geminiService;
    }

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @PostMapping("/analyze")
    @ResponseBody
    public String analyzeResume(
            @RequestParam("resume") MultipartFile resume) {

        try {

            // 1. Read uploaded PDF
            byte[] pdfBytes = resume.getBytes();

            // 2. Extract text using PDFBox / OCR
            String resumeText =
                    resumeTextExtractor.extractText(pdfBytes);

            System.out.println("=================================");
            System.out.println("EXTRACTED RESUME TEXT");
            System.out.println("=================================");
            System.out.println(resumeText);
            System.out.println("=================================");

            // 3. Send extracted text to Gemini
            String analysis =
                    geminiService.analyzeResume(resumeText);

            System.out.println("=================================");
            System.out.println("GEMINI RESUME ANALYSIS");
            System.out.println("=================================");
            System.out.println(analysis);
            System.out.println("=================================");

            // 4. Return AI analysis to frontend
            return analysis;

        } catch (Exception e) {

            e.printStackTrace();

            return "Error while analyzing resume.";
        }
    }
}