package com.slotmind.domain.event;

import com.slotmind.domain.enums.TriggerReason;
public record DisruptionEvent(String appointmentId, TriggerReason reason) {}
