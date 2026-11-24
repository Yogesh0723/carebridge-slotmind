package com.slotmind.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.slotmind.domain.enums.*;
import com.slotmind.domain.model.*;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuleBasedSchedulingStrategyTest {
  private final RuleBasedSchedulingStrategy strategy = new RuleBasedSchedulingStrategy();

  @Test
  void onlyAvailableMatchingSpecialtySlotsAreRecommended() {
    var general = new Provider("PRV-G", "Dr. General", ProviderSpecialty.GENERAL);
    var specialist = new Provider("PRV-S", "Dr. Specialist", ProviderSpecialty.SPECIALIST);
    var original = new Slot("OLD", general, LocalDateTime.now().plusHours(1), 30);
    original.book();
    var appointment = new Appointment("APT", "Patient", "contact", AppointmentType.ROUTINE, general, original);
    var matching = new Slot("MATCH", general, LocalDateTime.now().plusHours(2), 30);
    var wrongSpecialty = new Slot("WRONG", specialist, LocalDateTime.now().plusHours(3), 30);

    var result = strategy.recommend(appointment, TriggerReason.PROVIDER_OFFLINE, List.of(matching, wrongSpecialty));

    assertEquals(List.of("MATCH"), result.stream().map(x -> x.slot().getId()).toList());
    assertTrue(result.get(0).explanation().contains("specialty"));
  }

  @Test
  void recommendationsAreEarliestFirstAndLimitedToThree() {
    var provider = new Provider("PRV-G", "Dr. General", ProviderSpecialty.GENERAL);
    var original = new Slot("OLD", provider, LocalDateTime.now().plusHours(1), 30);
    original.book();
    var appointment = new Appointment("APT", "Patient", "contact", AppointmentType.ROUTINE, provider, original);
    var slots = java.util.stream.IntStream.range(0, 5)
        .mapToObj(i -> new Slot("S" + i, provider, LocalDateTime.now().plusHours(6 - i), 30)).toList();

    var result = strategy.recommend(appointment, TriggerReason.SLOT_CANCELLED, slots);

    assertEquals(3, result.size());
    assertEquals("S4", result.get(0).slot().getId());
  }
}