package com.slotmind.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.slotmind.domain.enums.AppointmentStatus;
import com.slotmind.domain.enums.AppointmentType;
import com.slotmind.domain.enums.ProviderSpecialty;
import com.slotmind.domain.enums.ProviderStatus;
import com.slotmind.domain.enums.SlotStatus;
import com.slotmind.domain.enums.SuggestionStatus;
import com.slotmind.domain.enums.TriggerReason;
import com.slotmind.domain.model.Appointment;
import com.slotmind.domain.model.Provider;
import com.slotmind.domain.model.RescheduleSuggestion;
import com.slotmind.domain.model.Slot;
import com.slotmind.repository.AppointmentRepository;
import com.slotmind.repository.ProviderRepository;
import com.slotmind.repository.SlotRepository;
import com.slotmind.repository.SuggestionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SuggestionServiceImplTest {
  private SuggestionRepository suggestions;
  private AppointmentRepository appointments;
  private SlotRepository slots;
  private ProviderRepository providers;
  private SuggestionServiceImpl service;

  @BeforeEach
  void setUp() {
    suggestions = mock(SuggestionRepository.class);
    appointments = mock(AppointmentRepository.class);
    slots = mock(SlotRepository.class);
    providers = mock(ProviderRepository.class);
    service = new SuggestionServiceImpl(suggestions, appointments, slots, providers);
  }

  @Test
  void acceptsSuggestionAtomically() {
    Provider drChen = new Provider("PRV-1", "Dr. Chen", ProviderSpecialty.GENERAL);
    Provider drSmith = new Provider("PRV-2", "Dr. Smith", ProviderSpecialty.GENERAL);

    Slot oldSlot = new Slot("SLT-OLD", drChen, LocalDateTime.now().plusDays(1), 30);
    oldSlot.book();

    Slot newSlot = new Slot("SLT-NEW", drSmith, LocalDateTime.now().plusDays(2), 30);

    Appointment appt = new Appointment("APT-1", "John", "555", AppointmentType.ROUTINE, drChen, oldSlot);
    appt.markPending();

    RescheduleSuggestion targetSuggestion = new RescheduleSuggestion(
        "SUG-1", appt, newSlot, drSmith, TriggerReason.PROVIDER_OFFLINE, 0.9, "reason", "script");

    RescheduleSuggestion otherSuggestion = new RescheduleSuggestion(
        "SUG-2", appt, new Slot("SLT-OTHER", drSmith, LocalDateTime.now().plusDays(3), 30),
        drSmith, TriggerReason.PROVIDER_OFFLINE, 0.8, "reason", "script");

    when(suggestions.findById("SUG-1")).thenReturn(Optional.of(targetSuggestion));
    when(suggestions.findByAppointmentId("APT-1")).thenReturn(List.of(targetSuggestion, otherSuggestion));
    when(suggestions.save(any(RescheduleSuggestion.class))).thenAnswer(i -> i.getArgument(0));

    RescheduleSuggestion accepted = service.accept("SUG-1");

    assertEquals(SuggestionStatus.ACCEPTED, accepted.getStatus());
    assertEquals(AppointmentStatus.RESCHEDULED, appt.getStatus());
    assertEquals(newSlot, appt.getSlot());
    assertEquals(SlotStatus.BOOKED, newSlot.getStatus());
    assertEquals(SlotStatus.OPEN, oldSlot.getStatus());
    assertEquals(SuggestionStatus.REJECTED, otherSuggestion.getStatus());

    verify(slots, times(1)).save(oldSlot);
    verify(suggestions, times(1)).save(targetSuggestion);
  }

  @Test
  void createHandlesMultiplePriorSuggestionsGracefully() {
    Provider drChen = new Provider("PRV-1", "Dr. Chen", ProviderSpecialty.GENERAL);
    Provider drSmith = new Provider("PRV-2", "Dr. Smith", ProviderSpecialty.GENERAL);
    drSmith.changeStatus(ProviderStatus.AVAILABLE);

    Slot slot = new Slot("SLT-5", drSmith, LocalDateTime.now().plusDays(1), 30);
    Appointment appt = new Appointment("APT-1", "John", "555", AppointmentType.ROUTINE, drChen, slot);
    appt.markPending();

    RescheduleSuggestion oldRejected = new RescheduleSuggestion(
        "SUG-OLD", appt, slot, drSmith, TriggerReason.PROVIDER_OFFLINE, 0.9, "reason", "script");
    oldRejected.reject();

    when(slots.findById("SLT-5")).thenReturn(Optional.of(slot));
    when(appointments.findById("APT-1")).thenReturn(Optional.of(appt));
    when(suggestions.findByAppointmentIdAndSlotId("APT-1", "SLT-5")).thenReturn(List.of(oldRejected));
    when(suggestions.save(any(RescheduleSuggestion.class))).thenAnswer(i -> i.getArgument(0));

    RescheduleSuggestion created = service.create("APT-1", "SLT-5", TriggerReason.PROVIDER_OFFLINE, 0.95, "New explanation", "script", "PRV-2");
    assertNotNull(created);
    assertEquals(SuggestionStatus.PENDING, created.getStatus());
  }
}
