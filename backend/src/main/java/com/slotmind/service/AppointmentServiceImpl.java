package com.slotmind.service;

import com.slotmind.domain.enums.AppointmentStatus;
import com.slotmind.domain.enums.AppointmentType;
import com.slotmind.domain.enums.ProviderStatus;
import com.slotmind.domain.enums.SlotStatus;
import com.slotmind.domain.enums.TriggerReason;
import com.slotmind.domain.event.DisruptionEvent;
import com.slotmind.domain.model.Appointment;
import com.slotmind.domain.model.Provider;
import com.slotmind.domain.model.Slot;
import com.slotmind.repository.AppointmentRepository;
import com.slotmind.repository.ProviderRepository;
import com.slotmind.repository.SlotRepository;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AppointmentServiceImpl implements AppointmentService {
  private final AppointmentRepository appointments;
  private final ProviderRepository providers;
  private final SlotRepository slots;
  private final ApplicationEventPublisher events;

  public AppointmentServiceImpl(AppointmentRepository appointments, ProviderRepository providers,
      SlotRepository slots, ApplicationEventPublisher events) {
    this.appointments = appointments;
    this.providers = providers;
    this.slots = slots;
    this.events = events;
  }

  @Override
  public List<Appointment> findAll() { return appointments.findAll(); }

  @Override
  @Transactional
  public Appointment create(String n, String c, AppointmentType t, String pid, String sid,
      String room, String equipment, String preference) {
    if (!StringUtils.hasText(n) || !StringUtils.hasText(c) || t == null || !StringUtils.hasText(pid)
        || !StringUtils.hasText(sid)) {
      throw new IllegalArgumentException("patientName, patientContact, type, providerId, and slotId are required");
    }
    Provider provider = providers.findById(pid).orElseThrow(() -> new IllegalArgumentException("Provider not found"));
    if (provider.getStatus() != ProviderStatus.AVAILABLE) throw new IllegalStateException("Provider is not available for new appointments");
    Slot slot = slots.findById(sid).orElseThrow();
    if (!slot.getProvider().getId().equals(pid)) throw new IllegalStateException("Slot does not belong to provider");
    if (slot.getProvider().getStatus() != ProviderStatus.AVAILABLE || slot.getStatus() != SlotStatus.OPEN) throw new IllegalStateException("Selected slot is not currently bookable");
    int requiredDuration = switch (t) { case CONSULTATION -> 45; case PROCEDURE -> 60; default -> 30; };
    if (slot.getDurationMinutes() < requiredDuration) throw new IllegalStateException("Slot duration is too short for appointment type");
    if (StringUtils.hasText(preference)) {
      int hour = slot.getStartTime().getHour();
      if ("MORNING".equalsIgnoreCase(preference) && hour >= 12) throw new IllegalStateException("Selected slot does not match morning preference");
      if ("AFTERNOON".equalsIgnoreCase(preference) && hour < 12) throw new IllegalStateException("Selected slot does not match afternoon preference");
    }
    var day = slot.getStartTime().toLocalDate();
    if (appointments.countByProviderIdAndSlotStartTimeBetween(pid, day.atStartOfDay(), day.plusDays(1).atStartOfDay()) >= provider.getMaxDailyAppointments()) throw new IllegalStateException("Provider daily appointment limit reached");
    slot.book();
    Appointment appointment = new Appointment("APT-" + System.currentTimeMillis(), n, c, t, provider, slot);
    appointment.setRoomRequirement(room); appointment.setEquipmentRequirement(equipment); appointment.setPatientPreference(preference);
    return appointments.save(appointment);
  }

  @Override
  @Transactional
  public Appointment cancel(String id) {
    Appointment appointment = appointments.findById(id).orElseThrow();
    if (appointment.getStatus() == AppointmentStatus.CANCELLED) throw new IllegalStateException("Appointment already cancelled");
    appointment.cancel(); appointment.getSlot().release(); slots.save(appointment.getSlot());
    return appointments.save(appointment);
  }

  @Override
  @Transactional
  public Appointment disrupt(String id, TriggerReason reason) {
    Appointment appointment = appointments.findById(id).orElseThrow();
    appointment.markPending(); Appointment saved = appointments.save(appointment);
    events.publishEvent(new DisruptionEvent(saved.getId(), reason)); return saved;
  }

  @Override
  public Appointment findById(String id) { return appointments.findById(id).orElseThrow(); }
}