package com.slotmind.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.slotmind.domain.enums.AppointmentStatus;
import com.slotmind.domain.enums.AppointmentType;
import com.slotmind.domain.enums.ProviderSpecialty;
import com.slotmind.domain.enums.ProviderStatus;
import com.slotmind.domain.enums.SlotStatus;
import com.slotmind.domain.enums.TriggerReason;
import com.slotmind.domain.model.Appointment;
import com.slotmind.domain.model.Provider;
import com.slotmind.domain.model.Slot;
import com.slotmind.repository.AppointmentRepository;
import com.slotmind.repository.ProviderRepository;
import com.slotmind.repository.SlotRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class AppointmentServiceImplTest {
  private AppointmentRepository appointments;
  private ProviderRepository providers;
  private SlotRepository slots;
  private ApplicationEventPublisher events;
  private AppointmentServiceImpl service;

  @BeforeEach
  void setUp() {
    appointments = mock(AppointmentRepository.class);
    providers = mock(ProviderRepository.class);
    slots = mock(SlotRepository.class);
    events = mock(ApplicationEventPublisher.class);
    service = new AppointmentServiceImpl(appointments, providers, slots, events);
  }

  @Test
  void createsAppointmentSuccessfullyWhenValid() {
    Provider drChen = new Provider("PRV-1", "Dr. Chen", ProviderSpecialty.GENERAL);
    drChen.changeStatus(ProviderStatus.AVAILABLE);

    Slot slot = new Slot("SLT-1", drChen, LocalDateTime.now().plusDays(1).withHour(10), 30);

    when(providers.findById("PRV-1")).thenReturn(Optional.of(drChen));
    when(slots.findById("SLT-1")).thenReturn(Optional.of(slot));
    when(appointments.countByProviderIdAndSlotStartTimeBetween(any(), any(), any())).thenReturn(0L);
    when(appointments.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Appointment result = service.create("Jane Doe", "555-9876", AppointmentType.ROUTINE,
        "PRV-1", "SLT-1", "Room 1", "Ultrasound", "MORNING");

    assertNotNull(result);
    assertEquals("Jane Doe", result.getPatientName());
    assertEquals(SlotStatus.BOOKED, slot.getStatus());
    verify(appointments, times(1)).save(any(Appointment.class));
  }

  @Test
  void throwsExceptionWhenProviderNotAvailable() {
    Provider drChen = new Provider("PRV-1", "Dr. Chen", ProviderSpecialty.GENERAL);
    drChen.changeStatus(ProviderStatus.OFFLINE);

    when(providers.findById("PRV-1")).thenReturn(Optional.of(drChen));

    assertThrows(IllegalStateException.class, () ->
        service.create("Jane Doe", "555-9876", AppointmentType.ROUTINE,
            "PRV-1", "SLT-1", null, null, null));
  }

  @Test
  void cancelReleasesSlot() {
    Provider drChen = new Provider("PRV-1", "Dr. Chen", ProviderSpecialty.GENERAL);
    Slot slot = new Slot("SLT-1", drChen, LocalDateTime.now().plusDays(1), 30);
    slot.book();
    Appointment appt = new Appointment("APT-1", "Jane", "555-1", AppointmentType.ROUTINE, drChen, slot);

    when(appointments.findById("APT-1")).thenReturn(Optional.of(appt));
    when(appointments.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));

    Appointment cancelled = service.cancel("APT-1");
    assertEquals(AppointmentStatus.CANCELLED, cancelled.getStatus());
    assertEquals(SlotStatus.OPEN, slot.getStatus());
    verify(slots, times(1)).save(slot);
  }

  @Test
  void completeTransitionsStatusToCompleted() {
    Provider drChen = new Provider("PRV-1", "Dr. Chen", ProviderSpecialty.GENERAL);
    Slot slot = new Slot("SLT-1", drChen, LocalDateTime.now().plusDays(1), 30);
    slot.book();
    Appointment appt = new Appointment("APT-1", "Jane", "555-1", AppointmentType.ROUTINE, drChen, slot);

    when(appointments.findById("APT-1")).thenReturn(Optional.of(appt));
    when(appointments.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));

    Appointment completed = service.complete("APT-1");
    assertEquals(AppointmentStatus.COMPLETED, completed.getStatus());
  }

  @Test
  void disruptPublishesDisruptionEvent() {
    Provider drChen = new Provider("PRV-1", "Dr. Chen", ProviderSpecialty.GENERAL);
    Slot slot = new Slot("SLT-1", drChen, LocalDateTime.now().plusDays(1), 30);
    slot.book();
    Appointment appt = new Appointment("APT-1", "Jane", "555-1", AppointmentType.ROUTINE, drChen, slot);

    when(appointments.findById("APT-1")).thenReturn(Optional.of(appt));
    when(appointments.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));

    Appointment disrupted = service.disrupt("APT-1", TriggerReason.SLOT_CANCELLED);
    assertEquals(AppointmentStatus.RESCHEDULE_PENDING, disrupted.getStatus());
    verify(events, times(1)).publishEvent(any(Object.class));
  }
}
