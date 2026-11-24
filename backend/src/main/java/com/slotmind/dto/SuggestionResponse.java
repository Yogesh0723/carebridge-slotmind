package com.slotmind.dto;

import com.slotmind.domain.enums.SuggestionStatus;
import com.slotmind.domain.enums.TriggerReason;
import com.slotmind.domain.model.RescheduleSuggestion;
import java.time.LocalDateTime;

public record SuggestionResponse(
    String id,
    String appointmentId,
    String slotId,
    SuggestionStatus status,
    TriggerReason reason,
    int recommendationRank,
    double confidence,
    String explanation,
    String patientScript,
    LocalDateTime startTime,
    String providerName,
    String alternateProviderId) {
  public static SuggestionResponse from(RescheduleSuggestion suggestion) {
    return new SuggestionResponse(
        suggestion.getId(), suggestion.getAppointment().getId(), suggestion.getSlot().getId(),
        suggestion.getStatus(), suggestion.getReason(), suggestion.getRecommendationRank(), suggestion.getConfidence(),
        suggestion.getExplanation(), suggestion.getPatientScript(), suggestion.getSlot().getStartTime(),
        suggestion.getSlot().getProvider().getName(),
        suggestion.getAlternateProvider() == null ? null : suggestion.getAlternateProvider().getId());
  }
}