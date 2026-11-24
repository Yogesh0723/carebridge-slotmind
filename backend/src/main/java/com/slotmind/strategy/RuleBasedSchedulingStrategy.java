package com.slotmind.strategy;

import com.slotmind.domain.enums.TriggerReason;
import com.slotmind.domain.model.Appointment;
import com.slotmind.domain.model.Slot;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic rule-based scheduling strategy:
 * 1. Matches doctor specialty to patient need
 * 2. Strongly prefers the original doctor for clinical continuity
 * 3. Prefers slots matching appointment duration and time-of-day preferences
 * 4. Sorts by earliest available time and returns top 3 candidates
 */
public class RuleBasedSchedulingStrategy implements SchedulingStrategy {

  @Override
  public List<Recommendation> recommend(Appointment a, TriggerReason r, List<Slot> slots) {
    if (slots == null || slots.isEmpty()) {
      return List.of();
    }

    List<Slot> sameSpecialty = slots.stream()
        .filter(s -> s.getProvider().getSpecialty() == a.getProvider().getSpecialty())
        .toList();

    boolean crossCoverage = sameSpecialty.isEmpty();
    List<Slot> candidatePool = crossCoverage ? slots : sameSpecialty;

    AtomicInteger rank = new AtomicInteger(1);
    return candidatePool.stream()
        .sorted(Comparator
            .comparing((Slot s) -> !s.getProvider().getId().equals(a.getProvider().getId()))
            .thenComparing((Slot s) -> s.getDurationMinutes() < a.getDurationMinutes())
            .thenComparing((Slot s) -> {
              if (a.getPatientPreference() == null || a.getPatientPreference().isBlank()) return false;
              int hour = s.getStartTime().getHour();
              if ("MORNING".equalsIgnoreCase(a.getPatientPreference()) && hour >= 12) return true;
              if ("AFTERNOON".equalsIgnoreCase(a.getPatientPreference()) && hour < 12) return true;
              return false;
            })
            .thenComparing(Slot::getStartTime))
        .limit(3)
        .map(s -> {
          boolean sameDoc = s.getProvider().getId().equals(a.getProvider().getId());
          if (crossCoverage) {
            String explanation = "All " + a.getProvider().getSpecialty()
                + " providers are currently offline. Recommended cross-coverage with colleague "
                + s.getProvider().getName() + " (" + s.getProvider().getSpecialty() + ") to maintain care continuity.";
            String script = "Hello " + a.getPatientName() + ", our " + a.getProvider().getSpecialty()
                + " providers are unavailable today, but we arranged a cross-coverage opening with "
                + s.getProvider().getName() + " (" + s.getProvider().getSpecialty() + ") on "
                + s.getStartTime() + ". Would this work for you?";
            return new Recommendation(s, rank.getAndIncrement(), 0.65, explanation, script, s.getProvider().getId());
          }

          String explanation = sameDoc
              ? "Matched specialty " + a.getProvider().getSpecialty() + " and maintained care continuity with " + s.getProvider().getName() + " at earliest available opening."
              : "Matched specialty " + a.getProvider().getSpecialty() + " with colleague " + s.getProvider().getName() + " for earliest recovery.";
          String script = sameDoc
              ? "Hello " + a.getPatientName() + ", we reserved a new opening with " + s.getProvider().getName()
                  + " on " + s.getStartTime() + ". Would this time suit you?"
              : "Hello " + a.getPatientName() + ", Dr. " + a.getProvider().getName()
                  + " is unavailable, but we found an opening with colleague " + s.getProvider().getName()
                  + " on " + s.getStartTime() + ". Would you like to take this appointment?";
          return new Recommendation(s, rank.getAndIncrement(), sameDoc ? 0.95 : 0.85, explanation, script, s.getProvider().getId());
        })
        .toList();
  }
}
