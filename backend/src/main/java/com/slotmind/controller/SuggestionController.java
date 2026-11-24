package com.slotmind.controller;

import com.slotmind.dto.SuggestionResponse;
import com.slotmind.service.SuggestionService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/suggestions")
public class SuggestionController {
  private final SuggestionService suggestionService;

  public SuggestionController(SuggestionService suggestionService) {
    this.suggestionService = suggestionService;
  }

  @PostMapping("/{id}/accept")
  public SuggestionResponse acceptSuggestion(@PathVariable String id) {
    return SuggestionResponse.from(suggestionService.accept(id));
  }

  @PostMapping("/{id}/reject")
  public SuggestionResponse rejectSuggestion(@PathVariable String id) {
    return SuggestionResponse.from(suggestionService.reject(id));
  }
}