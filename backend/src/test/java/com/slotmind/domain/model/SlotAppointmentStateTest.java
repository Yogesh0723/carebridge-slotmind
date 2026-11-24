package com.slotmind.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.slotmind.domain.enums.AppointmentStatus;
import com.slotmind.domain.enums.AppointmentType;
import com.slotmind.domain.enums.ProviderSpecialty;
import com.slotmind.domain.enums.SlotStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class SlotAppointmentStateTest {

  @Test
  void bookingAnAvailableSlotMakesItBooked() {
    Provider provider = new Provider("Dr. Test", ProviderSpecialty.GENERAL);
    Slot slot = new Slot(provider, LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(2));

    slot.book();

    assertEquals(SlotStatus.BOOKED, slot.getStatus());
    assertThrows(IllegalStateException.class, slot::book);
  }

  @Test
  void disruptionAndRescheduleFollowTheExpectedLifecycle() {
    Provider provider = new Provider("Dr. Test", ProviderSpecialty.GENERAL);
    Slot oldSlot = new Slot(provider, LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(2));
    oldSlot.book();
    Appointment appointment = new Appointment("Patient", "contact", AppointmentType.ROUTINE, provider, oldSlot);
    Slot newSlot = new Slot(provider, LocalDateTime.now().plusHours(3), LocalDateTime.now().plusHours(4));

    appointment.markPending();
    appointment.reschedule(newSlot);

    assertEquals(AppointmentStatus.RESCHEDULED, appointment.getStatus());
    assertEquals(newSlot, appointment.getSlot());
    assertThrows(IllegalStateException.class, appointment::markPending);
  }
}