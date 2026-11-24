package com.slotmind.dto;

import com.slotmind.domain.enums.ProviderStatus;
import com.slotmind.domain.enums.SlotStatus;
import com.slotmind.domain.model.Slot;
import java.time.LocalDateTime;

public record SlotResponse(
    String id,
    LocalDateTime startTime,
    int durationMinutes,
    SlotStatus status,
    String providerId,
    String providerName,
    ProviderStatus providerStatus,
    String room,
    String equipment) {
  public static SlotResponse from(Slot slot) {
    return new SlotResponse(
        slot.getId(), slot.getStartTime(), slot.getDurationMinutes(), slot.getStatus(),
        slot.getProvider().getId(), slot.getProvider().getName(), slot.getProvider().getStatus(),
        slot.getRoom(), slot.getEquipment());
  }
}