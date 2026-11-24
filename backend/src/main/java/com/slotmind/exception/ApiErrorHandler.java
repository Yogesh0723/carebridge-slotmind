package com.slotmind.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Converts expected domain and request failures into consistent API responses. */
@RestControllerAdvice
public class ApiErrorHandler {
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException ex) {
    return ResponseEntity.badRequest().body(Map.of("error", messageOrDefault(ex, "Invalid request")));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<Map<String, String>> illegalState(IllegalStateException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("error", messageOrDefault(ex, "Operation cannot be completed")));
  }

  private static String messageOrDefault(RuntimeException ex, String fallback) {
    return ex.getMessage() == null || ex.getMessage().isBlank() ? fallback : ex.getMessage();
  }
}