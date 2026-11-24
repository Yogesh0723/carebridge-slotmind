package com.slotmind.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.slotmind.domain.enums.AppointmentStatus;
import com.slotmind.domain.enums.AppointmentType;
import com.slotmind.domain.enums.ProviderSpecialty;
import com.slotmind.domain.enums.ProviderStatus;
import com.slotmind.domain.enums.SlotStatus;
import com.slotmind.domain.enums.TriggerReason;
import com.slotmind.domain.event.DisruptionEvent;
import com.slotmind.domain.model.Appointment;
import com.slotmind.domain.model.Provider;
import com.slotmind.domain.model.Slot;
import com.slotmind.repository.AppointmentRepository;
import com.slotmind.repository.SlotRepository;
import com.slotmind.service.SuggestionService;
import com.slotmind.strategy.Recommendation;
import com.slotmind.strategy.SchedulingStrategy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RescheduleAgentListenerTest {
  private AppointmentRepository appointments;
  private SlotRepository slots;
  private SchedulingStrategy strategy;
  private SuggestionService suggestions;
  private RescheduleAgentListener listener;

  @BeforeEach
  void setUp() {
    appointments = mock(AppointmentRepository.class);
    slots = mock(SlotRepository.class);
    strategy = mock(SchedulingStrategy.class);
    suggestions = mock(SuggestionService.class);
    listener = new RescheduleAgentListener(appointments, slots, strategy, suggestions);
  }

  @Test
  void handlesSlotCancelledDisruptionEvenWhenProviderIsAvailable() {
    Provider drChen = new Provider("PRV-1", "Dr. Chen", ProviderSpecialty.GENERAL);
    drChen.changeStatus(ProviderStatus.AVAILABLE);

    Slot oldSlot = new Slot("SLT-1", drChen, LocalDateTime.now().plusDays(1), 30);
    Appointment appointment = new Appointment("APT-1", "John", "555-1234", AppointmentType.ROUTINE, drChen, oldSlot);
    appointment.markPending(); // status is RESCHEDULE_PENDING

    when(appointments.findById("APT-1")).thenReturn(Optional.of(appointment));

    Slot newSlot = new Slot("SLT-NEW", drChen, LocalDateTime.now().plusDays(2), 30);
    when(slots.findByStatusAndProviderStatus(SlotStatus.OPEN, ProviderStatus.AVAILABLE))
        .thenReturn(List.of(newSlot));

    when(strategy.recommend(eq(appointment), eq(TriggerReason.SLOT_CANCELLED), any()))
        .thenReturn(List.of(new Recommendation(newSlot, 0.9, "Available replacement slot", "Script", null)));

    listener.handle(new DisruptionEvent("APT-1", TriggerReason.SLOT_CANCELLED));

    verify(suggestions, times(1)).create(
        eq("APT-1"), eq("SLT-NEW"), eq(TriggerReason.SLOT_CANCELLED), eq(1), eq(0.9), any(), any(), any());
  }

  @Test
  void skipsWhenAppointmentNotPending() {
    Provider drChen = new Provider("PRV-1", "Dr. Chen", ProviderSpecialty.GENERAL);
    Slot oldSlot = new Slot("SLT-1", drChen, LocalDateTime.now().plusDays(1), 30);
    Appointment appointment = new Appointment("APT-1", "John", "555-1234", AppointmentType.ROUTINE, drChen, oldSlot);
    // appointment is still CONFIRMED, not RESCHEDULE_PENDING

    when(appointments.findById("APT-1")).thenReturn(Optional.of(appointment));

    listener.handle(new DisruptionEvent("APT-1", TriggerReason.PROVIDER_OFFLINE));

    verify(strategy, never()).recommend(any(), any(), any());
    verify(suggestions, never()).create(any(), any(), any(), anyDouble(), any(), any(), any());
  }
}
