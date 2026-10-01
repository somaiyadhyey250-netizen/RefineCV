package com.resumeanalyzer.resume_analyzer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.unit.DataSize;

class ResumeControllerValidationTest {

    private final ResumeController controller = new ResumeController(
            null,
            null,
            DataSize.ofMegabytes(5),
            new ThreadPoolTaskExecutor(),
            new ThreadPoolTaskScheduler(),
            Duration.ofMinutes(3),
            Duration.ofSeconds(15),
            new InMemoryAnalysisRateLimiter(5, Duration.ofMinutes(15), 100)
    );

    @Test
    void rejectsMissingUpload() {
        assertThrows(ResumeValidationException.class, () -> controller.validateUpload(null));
    }

    @Test
    void rejectsEmptyUpload() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "resume", "resume.pdf", "application/pdf", new byte[0]
        );

        assertThrows(ResumeValidationException.class, () -> controller.validateUpload(emptyFile));
    }

    @Test
    void rejectsUploadAboveConfiguredLimit() {
        MockMultipartFile oversizedFile = new MockMultipartFile(
                "resume", "resume.pdf", "application/pdf", new byte[5 * 1024 * 1024 + 1]
        );

        assertThrows(ResumeValidationException.class, () -> controller.validateUpload(oversizedFile));
    }

    @Test
    void oversizedMultipartHandlerReturnsSafeClientMessage() {
        var response = new GlobalExceptionHandler().handleMaxUploadSizeExceeded(
                new org.springframework.web.multipart.MaxUploadSizeExceededException(1));

        assertEquals(413, response.getStatusCode().value());
        assertEquals(AnalysisErrorMessages.forCategory(AnalysisErrorMessages.Category.FILE_TOO_LARGE),
                response.getBody());
    }

    @Test
    void unexpectedMvcFailureGetsSafeGenericResponse() {
        var response = new GlobalExceptionHandler().handleUnexpectedException(
                new IllegalStateException("secret path and stack details"));

        assertEquals(500, response.getStatusCode().value());
        assertEquals(AnalysisErrorMessages.forCategory(AnalysisErrorMessages.Category.INTERNAL),
                response.getBody());
        assertFalse(response.getBody().contains("secret"));
    }

    @Test
    void missingStaticResourcePreservesNotFoundStatusAndHidesFrameworkDetails() {
        var exception = new org.springframework.web.servlet.resource.NoResourceFoundException(
                org.springframework.http.HttpMethod.GET, "/missing", "internal resource details");

        var response = new GlobalExceptionHandler().handleResourceNotFound(exception);

        assertEquals(404, response.getStatusCode().value());
        assertEquals("The requested resource was not found.", response.getBody());
        assertFalse(response.getBody().contains("internal"));
    }

    @Test
    void frameworkHttpExceptionPreservesStatusWithSafeMessage() {
        var exception = new org.springframework.web.ErrorResponseException(
                org.springframework.http.HttpStatus.BAD_REQUEST);

        var response = new GlobalExceptionHandler().handleFrameworkHttpException(exception);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("The request could not be processed.", response.getBody());
    }
}
