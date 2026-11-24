package com.slotmind.service;

import com.slotmind.domain.enums.*;
import com.slotmind.domain.model.*;
import com.slotmind.repository.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

@Service
public class SlotService {
  private final SlotRepository slots;
  private final ProviderRepository providers;
  private final AppointmentRepository appointments;
  private final ApplicationEventPublisher events;

  @Autowired
  public SlotService(SlotRepository s, ProviderRepository p, AppointmentRepository appointments, ApplicationEventPublisher events) {
    slots = s;
    providers = p;
    this.appointments = appointments;
    this.events = events;
  }

  public List<Slot> all() {
    return slots.findAll();
  }

  public Slot create(String pid, LocalDateTime start, int durationMinutes, String room, String equipment) {
    var slot = new Slot("SLT-" + System.currentTimeMillis(), providers.findById(pid).orElseThrow(), start, durationMinutes);
    slot.setRoom(room); slot.setEquipment(equipment); return slots.save(slot);
  }

  @Transactional
  public Slot cancel(String id) {
    var s = slots.findById(id).orElseThrow();
    s.cancel();
    var saved = slots.save(s);
    appointments.findBySlotId(id).filter(a -> a.getStatus() == AppointmentStatus.CONFIRMED).ifPresent(a -> {
      a.markPending();
      appointments.save(a);
      events.publishEvent(new com.slotmind.domain.event.DisruptionEvent(a.getId(), TriggerReason.SLOT_CANCELLED));
    });
    return saved;
  }

  public List<Slot> available() {
    return slots.findByStatusAndProviderStatus(SlotStatus.OPEN, ProviderStatus.AVAILABLE);
  }
}
