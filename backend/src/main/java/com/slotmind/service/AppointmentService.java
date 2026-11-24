package com.slotmind.service;

import com.slotmind.domain.enums.*;
import com.slotmind.domain.event.*;
import com.slotmind.domain.model.*;
import com.slotmind.repository.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AppointmentService {
  private final AppointmentRepository appointments;
  private final ProviderRepository providers;
  private final SlotRepository slots;
  private final ApplicationEventPublisher events;

  @Autowired
  public AppointmentService(
      AppointmentRepository a,
      ProviderRepository p,
      SlotRepository s,
      ApplicationEventPublisher e) {
    appointments = a;
    providers = p;
    slots = s;
    events = e;
  }

  public List<Appointment> all() {
    return appointments.findAll();
  }

  @Transactional
  public Appointment create(String n, String c, AppointmentType t, String pid, String sid, String room,
      String equipment, String preference) {
    if (!StringUtils.hasText(n) || !StringUtils.hasText(c) || t == null || !StringUtils.hasText(pid)
        || !StringUtils.hasText(sid))
      throw new IllegalArgumentException("patientName, patientContact, type, providerId, and slotId are required");
    var provider = providers.findById(pid).orElseThrow(() -> new IllegalArgumentException("Provider not found"));
    if (provider.getStatus() != ProviderStatus.AVAILABLE)
      throw new IllegalStateException("Provider is not available for new appointments");
    var slot = slots.findById(sid).orElseThrow();
    if (!slot.getProvider().getId().equals(pid))
      throw new IllegalStateException("Slot does not belong to provider");
    if (slot.getProvider().getStatus() != ProviderStatus.AVAILABLE || slot.getStatus() != SlotStatus.OPEN)
      throw new IllegalStateException("Selected slot is not currently bookable");
    int requiredDuration = switch (t) {
      case CONSULTATION -> 45;
      case PROCEDURE -> 60;
      default -> 30;
    };
    if (slot.getDurationMinutes() < requiredDuration)
      throw new IllegalStateException("Slot duration is too short for appointment type");
    if (StringUtils.hasText(preference)) {
      var hour = slot.getStartTime().getHour();
      if ("MORNING".equalsIgnoreCase(preference) && hour >= 12)
        throw new IllegalStateException("Selected slot does not match morning preference");
      if ("AFTERNOON".equalsIgnoreCase(preference) && hour < 12)
        throw new IllegalStateException("Selected slot does not match afternoon preference");
    }
    var day = slot.getStartTime().toLocalDate();
    int dailyLimit = provider.getMaxDailyAppointments();
    long bookedToday = appointments.countByProviderIdAndSlotStartTimeBetween(pid, day.atStartOfDay(),
        day.plusDays(1).atStartOfDay());
    if (bookedToday >= dailyLimit)
      throw new IllegalStateException("Provider daily appointment limit reached");
    slot.book();
    var a = new Appointment("APT-" + System.currentTimeMillis(), n, c, t, provider, slot);
    a.setRoomRequirement(room);
    a.setEquipmentRequirement(equipment);
    a.setPatientPreference(preference);
    appointments.save(a);
    return a;
  }

  @Transactional
  public Appointment cancel(String id) {
    var a = appointments.findById(id).orElseThrow();
    if (a.getStatus() == AppointmentStatus.CANCELLED)
      throw new IllegalStateException("Appointment already cancelled");
    a.cancel();
    a.getSlot().release();
    slots.save(a.getSlot());
    return appointments.save(a);
  }

  @Transactional
  public Appointment disrupt(String id, TriggerReason reason) {
    var a = appointments.findById(id).orElseThrow();
    a.markPending();
    var saved = appointments.save(a);
    events.publishEvent(new DisruptionEvent(saved.getId(), reason));
    return saved;
  }

  public Appointment get(String id) {
    return appointments.findById(id).orElseThrow();
  }
}
