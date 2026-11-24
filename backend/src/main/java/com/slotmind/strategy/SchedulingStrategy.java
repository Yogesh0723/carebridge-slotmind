package com.slotmind.strategy;

import com.slotmind.domain.enums.TriggerReason;
import com.slotmind.domain.model.Appointment;
import com.slotmind.domain.model.Slot;
import java.util.List;

public interface SchedulingStrategy {
  List<Recommendation> recommend(Appointment appointment, TriggerReason reason, List<Slot> slots);
}
