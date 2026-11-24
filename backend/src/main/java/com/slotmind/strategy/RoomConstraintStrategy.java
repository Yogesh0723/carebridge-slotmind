package com.slotmind.strategy;

import com.slotmind.domain.enums.TriggerReason;
import com.slotmind.domain.model.Appointment;
import com.slotmind.domain.model.Slot;
import java.util.Comparator;
import java.util.List;

/** Sprint 2 strategy: filters slots by requested room/equipment before ranking them. */
public class RoomConstraintStrategy implements SchedulingStrategy {
  @Override
  public List<Recommendation> recommend(Appointment appointment, TriggerReason reason, List<Slot> slots) {
    return slots.stream()
        .filter(slot -> appointment.getRoomRequirement() == null || appointment.getRoomRequirement().isBlank()
            || appointment.getRoomRequirement().equalsIgnoreCase(slot.getRoom()))
        .filter(slot -> appointment.getEquipmentRequirement() == null || appointment.getEquipmentRequirement().isBlank()
            || appointment.getEquipmentRequirement().equalsIgnoreCase(slot.getEquipment()))
        .filter(slot -> slot.getDurationMinutes() >= appointment.getDurationMinutes())
        .sorted(Comparator.comparing(Slot::getStartTime))
        .limit(3)
        .map(slot -> new Recommendation(slot, .9, "Matched room, equipment, duration, and earliest available time.",
            "We found a suitable room and equipment for your appointment at " + slot.getStartTime() + "."))
        .toList();
  }
}