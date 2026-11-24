package com.slotmind.dto;

import com.slotmind.domain.enums.AppointmentType;

public record AppointmentRequest(
    String patientName,
    String patientContact,
    AppointmentType type,
    String providerId,
    String providerName,
    String slotId,
    String roomRequirement,
    String equipmentRequirement,
    String patientPreference) {}