package com.slotmind.service;

import com.slotmind.domain.model.Slot;
import java.time.LocalDateTime;
import java.util.List;

public interface SlotService {
  List<Slot> findAll();
  List<Slot> findAvailable();
  Slot create(String providerId, LocalDateTime startTime, int durationMinutes, String room, String equipment);
  Slot cancel(String id);
  Slot findById(String id);
}