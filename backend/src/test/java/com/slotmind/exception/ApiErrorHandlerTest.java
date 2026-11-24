package com.slotmind.exception;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ApiErrorHandlerTest {
  private final ApiErrorHandler handler = new ApiErrorHandler();

  @Test
  void handlesIllegalArgumentException() {
    ResponseEntity<Map<String, String>> response = handler.badRequest(new IllegalArgumentException("Invalid input"));
    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertEquals("Invalid input", response.getBody().get("error"));
  }

  @Test
  void handlesIllegalStateException() {
    ResponseEntity<Map<String, String>> response = handler.illegalState(new IllegalStateException("Conflict occurred"));
    assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    assertEquals("Conflict occurred", response.getBody().get("error"));
  }

  @Test
  void handlesNoSuchElementException() {
    ResponseEntity<Map<String, String>> response = handler.notFound(new NoSuchElementException("Not found"));
    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    assertEquals("Not found", response.getBody().get("error"));
  }
}
