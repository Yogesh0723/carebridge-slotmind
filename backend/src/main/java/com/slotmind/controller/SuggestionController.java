package com.slotmind.controller;

import com.slotmind.dto.SuggestionResponse;
import com.slotmind.service.SuggestionService;
import java.util.Map;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/suggestions", "/suggestions"})
public class SuggestionController {
  private final SuggestionService suggestionService;

  public SuggestionController(SuggestionService suggestionService) {
    this.suggestionService = suggestionService;
  }

  @PatchMapping("/{id}")
  public SuggestionResponse patchSuggestion(
      @PathVariable String id,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String action,
      @RequestBody(required = false) Map<String, Object> body) {
    String decision = status != null ? status : (action != null ? action : "");
    if (decision.isBlank() && body != null) {
      if (body.containsKey("status")) decision = String.valueOf(body.get("status"));
      else if (body.containsKey("action")) decision = String.valueOf(body.get("action"));
    }
    if ("reject".equalsIgnoreCase(decision) || "rejected".equalsIgnoreCase(decision)) {
      return SuggestionResponse.from(suggestionService.reject(id));
    }
    return SuggestionResponse.from(suggestionService.accept(id));
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
