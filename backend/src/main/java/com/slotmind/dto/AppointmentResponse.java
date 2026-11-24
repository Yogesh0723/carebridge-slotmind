package com.slotmind.dto;

import com.slotmind.domain.enums.AppointmentStatus;
import com.slotmind.domain.enums.AppointmentType;
import com.slotmind.domain.model.Appointment;
import java.time.LocalDateTime;

public record AppointmentResponse(
    String id,
    String patientName,
    AppointmentType type,
    AppointmentStatus status,
    String providerId,
    String providerName,
    String slotId,
    LocalDateTime startTime) {
  public static AppointmentResponse from(Appointment appointment) {
    return new AppointmentResponse(
        appointment.getId(), appointment.getPatientName(), appointment.getType(),
        appointment.getStatus(), appointment.getProvider().getId(),
        appointment.getProvider().getName(), appointment.getSlot().getId(),
        appointment.getSlot().getStartTime());
  }
}