package com.resumeanalyzer.resume_analyzer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Safe fallback for synchronous MVC errors; SSE analysis errors are handled by the stream boundary. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<String> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .contentType(MediaType.TEXT_PLAIN)
                .body(AnalysisErrorMessages.forCategory(AnalysisErrorMessages.Category.FILE_TOO_LARGE));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<String> handleResourceNotFound(NoResourceFoundException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .contentType(MediaType.TEXT_PLAIN)
                .body("The requested resource was not found.");
    }

    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<String> handleFrameworkHttpException(ErrorResponseException exception) {
        var status = exception.getStatusCode();
        String message = status.is4xxClientError()
                ? "The request could not be processed."
                : AnalysisErrorMessages.forCategory(AnalysisErrorMessages.Category.INTERNAL);
        return ResponseEntity.status(status)
                .contentType(MediaType.TEXT_PLAIN)
                .body(message);
    }

    @ExceptionHandler(org.springframework.web.context.request.async.AsyncRequestTimeoutException.class)
    public ResponseEntity<java.util.Map<String, String>> handleAsyncRequestTimeout(
            org.springframework.web.context.request.async.AsyncRequestTimeoutException exception
    ) {
        logger.warn("mvc_async_request_timeout");
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(java.util.Map.of("error", AnalysisErrorMessages.forCategory(AnalysisErrorMessages.Category.TIMEOUT)));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<java.util.Map<String, String>> handleHttpMessageNotReadable(
            org.springframework.http.converter.HttpMessageNotReadableException exception
    ) {
        logger.warn("invalid_request_payload: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(java.util.Map.of("error", "Invalid request payload."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleUnexpectedException(Exception exception) {
        logger.error("unhandled_mvc_request_failure errorType={}", exception.getClass().getName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.TEXT_PLAIN)
                .body(AnalysisErrorMessages.forCategory(AnalysisErrorMessages.Category.INTERNAL));
    }
}
