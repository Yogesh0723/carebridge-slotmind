package com.slotmind.dto;

import com.slotmind.domain.enums.*;
import com.slotmind.domain.model.*;
import java.time.*;

public final class Dtos {
  private Dtos() {
  }

  public record ProviderRequest(String name, ProviderSpecialty specialty) {
  }

  public record SlotRequest(String providerId, LocalDateTime startTime, int durationMinutes, String room,
      String equipment) {
  }

  public record AppointmentRequest(
      String patientName,
      String patientContact,
      AppointmentType type,
      String providerId,
      String providerName,
      String slotId,
      String roomRequirement,
      String equipmentRequirement,
      String patientPreference) {
  }

  public record SuggestionResponse(
      String id,
      String appointmentId,
      String slotId,
      SuggestionStatus status,
      TriggerReason reason,
      double confidence,
      String explanation,
      String patientScript,
      LocalDateTime startTime,
       String providerName,
       String alternateProviderId) {
    public static SuggestionResponse from(RescheduleSuggestion s) {
      return new SuggestionResponse(
          s.getId(),
          s.getAppointment().getId(),
          s.getSlot().getId(),
          s.getStatus(),
          s.getReason(),
          s.getConfidence(),
          s.getExplanation(),
          s.getPatientScript(),
          s.getSlot().getStartTime(),
          s.getSlot().getProvider().getName(),
          s.getAlternateProvider() == null ? null : s.getAlternateProvider().getId());
    }
  }

  public record AppointmentResponse(
      String id,
      String patientName,
      AppointmentType type,
      AppointmentStatus status,
      String providerId,
      String providerName,
      String slotId,
      LocalDateTime startTime) {
    public static AppointmentResponse from(Appointment a) {
      return new AppointmentResponse(
          a.getId(),
          a.getPatientName(),
          a.getType(),
          a.getStatus(),
          a.getProvider().getId(),
          a.getProvider().getName(),
          a.getSlot().getId(),
          a.getSlot().getStartTime());
    }
  }
}
