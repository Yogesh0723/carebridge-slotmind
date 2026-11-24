package com.slotmind.dto;

import java.time.LocalDateTime;

public record SlotRequest(
    String providerId,
    LocalDateTime startTime,
    int durationMinutes,
    String room,
    String equipment) {}