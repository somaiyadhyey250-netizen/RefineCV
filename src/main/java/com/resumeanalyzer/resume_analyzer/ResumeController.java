package com.resumeanalyzer.resume_analyzer;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class ResumeController {


    private final ResumeTextExtractor resumeTextExtractor;

    private final GeminiService geminiService;


    public ResumeController(
            ResumeTextExtractor resumeTextExtractor,
            GeminiService geminiService
    ) {

        this.resumeTextExtractor =
                resumeTextExtractor;

        this.geminiService =
                geminiService;

    }


    /* =========================================
       HOME PAGE
    ========================================= */

    @GetMapping("/")
    public String home() {

        return "index";

    }


    /* =========================================
       RESUME ANALYSIS
    ========================================= */

    @PostMapping(
            value = "/analyze",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    @ResponseBody
    public SseEmitter analyzeResume(
            @RequestParam("resume")
            MultipartFile resume
    ) {


        /*
         * SseEmitter keeps the HTTP connection
         * open while the resume is being processed.
         *
         * This allows the backend to send
         * progress updates to the browser.
         */

        SseEmitter emitter =
                new SseEmitter(
                        120000L
                );


        CompletableFuture.runAsync(
                () -> {

                    try {


                        // =====================================
                        // STEP 1 — UPLOAD RECEIVED
                        // =====================================

                        sendStatus(
                                emitter,
                                "upload"
                        );


                        /*
                         * Read uploaded PDF.
                         */

                        byte[] pdfBytes =
                                resume.getBytes();


                        // =====================================
                        // STEP 2 + 3 — EXTRACTION / OCR
                        // =====================================

                        String resumeText =
                                resumeTextExtractor.extractText(

                                        pdfBytes,

                                        status -> {

                                            sendStatus(
                                                    emitter,
                                                    status
                                            );

                                        }

                                );


                        // =====================================
                        // STEP 4 — GEMINI AI
                        // =====================================

                        String analysis =
                                geminiService.analyzeResume(

                                        resumeText,

                                        status -> {

                                            sendStatus(
                                                    emitter,
                                                    status
                                            );

                                        }

                                );


                        // =====================================
                        // STEP 5 — FINAL RESULT
                        // =====================================

                        sendEvent(
                                emitter,
                                "result",
                                analysis
                        );


                        /*
                         * Close the SSE connection.
                         *
                         * The frontend receives the
                         * result event and immediately
                         * switches to the results page.
                         */

                        emitter.complete();


                    } catch (Exception e) {


                        e.printStackTrace();


                        sendEvent(
                                emitter,
                                "error",
                                "Error while analyzing resume."
                        );


                        emitter.completeWithError(
                                e
                        );

                    }

                }
        );


        return emitter;

    }


    /* =========================================
       SEND STATUS
    ========================================= */

    private void sendStatus(
            SseEmitter emitter,
            String status
    ) {


        sendEvent(
                emitter,
                "status",
                status
        );

    }


    /* =========================================
       SEND SSE EVENT
    ========================================= */

    private void sendEvent(
            SseEmitter emitter,
            String eventName,
            String data
    ) {


        try {


            emitter.send(

                    SseEmitter.event()

                            .name(eventName)

                            .data(data)

            );


        } catch (IOException e) {


            emitter.completeWithError(
                    e
            );

        }

    }

}