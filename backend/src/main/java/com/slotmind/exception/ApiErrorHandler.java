package com.slotmind.exception;

import java.util.Map;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Converts expected domain and request failures into consistent API responses. */
@RestControllerAdvice
public class ApiErrorHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException ex) {
    return ResponseEntity.badRequest().body(Map.of("error", messageOrDefault(ex, "Invalid request")));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<Map<String, String>> illegalState(IllegalStateException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("error", messageOrDefault(ex, "Operation cannot be completed")));
  }

  @ExceptionHandler(NoSuchElementException.class)
  public ResponseEntity<Map<String, String>> notFound(NoSuchElementException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(Map.of("error", messageOrDefault(ex, "Resource not found")));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, String>> unhandled(Exception ex) {
    log.error("Unhandled exception in API request: ", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(Map.of("error", "An internal error occurred: " + messageOrDefault(new RuntimeException(ex.getMessage()), "Internal error")));
  }

  private static String messageOrDefault(RuntimeException ex, String fallback) {
    return ex.getMessage() == null || ex.getMessage().isBlank() ? fallback : ex.getMessage();
  }
}
