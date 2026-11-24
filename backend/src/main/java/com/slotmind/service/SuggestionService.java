package com.slotmind.service;

import com.slotmind.domain.enums.TriggerReason;
import com.slotmind.domain.model.RescheduleSuggestion;
import java.util.List;

public interface SuggestionService {
  List<RescheduleSuggestion> findForAppointment(String id);
  void clearPendingForAppointment(String appointmentId);
  RescheduleSuggestion accept(String id);
  RescheduleSuggestion reject(String id);
  RescheduleSuggestion create(String appointmentId, String slotId, TriggerReason reason,
      double confidence, String explanation, String patientScript, String alternateProviderId);

  RescheduleSuggestion create(String appointmentId, String slotId, TriggerReason reason,
      int recommendationRank, double confidence, String explanation, String patientScript,
      String alternateProviderId);
}