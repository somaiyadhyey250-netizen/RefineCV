package com.resumeanalyzer.resume_analyzer;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lightweight health check endpoint for Render keep-alive and platform liveness probes.
 * Performs zero AI, OCR, storage, or external service calls.
 */
@RestController
public class HealthController {

    private static final Map<String, String> HEALTH_RESPONSE = Map.of("status", "UP");

    @GetMapping(value = "/healthz", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> healthz() {
        return ResponseEntity.ok(HEALTH_RESPONSE);
    }
}
