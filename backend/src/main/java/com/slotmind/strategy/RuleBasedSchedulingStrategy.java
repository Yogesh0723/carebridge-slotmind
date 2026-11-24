package com.slotmind.strategy;

import com.slotmind.domain.enums.TriggerReason;
import com.slotmind.domain.model.Appointment;
import com.slotmind.domain.model.Slot;
import java.util.Comparator;
import java.util.List;

public class RuleBasedSchedulingStrategy implements SchedulingStrategy {
  public List<Recommendation> recommend(Appointment a, TriggerReason r, List<Slot> slots) {
    return slots.stream()
        .filter(s -> s.getProvider().getSpecialty() == a.getProvider().getSpecialty())
        .sorted(Comparator
            .comparing((Slot s) -> !s.getProvider().getId().equals(a.getProvider().getId()))
            .thenComparing(Slot::getStartTime))
        .limit(3)
        .map(
            s ->
                new Recommendation(
                    s,
                    .82,
                    "Matched specialty and earliest available time.",
                    "We found an earlier suitable appointment with "
                        + s.getProvider().getName()
                        + ". Would you like to move it to "
                        + s.getStartTime()
                        + "?"))
        .toList();
  }
}
