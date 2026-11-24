package com.slotmind.service;

import com.slotmind.domain.enums.AppointmentStatus;
import com.slotmind.domain.enums.ProviderStatus;
import com.slotmind.domain.enums.SlotStatus;
import com.slotmind.domain.enums.TriggerReason;
import com.slotmind.domain.event.DisruptionEvent;
import com.slotmind.domain.model.Slot;
import com.slotmind.repository.AppointmentRepository;
import com.slotmind.repository.ProviderRepository;
import com.slotmind.repository.SlotRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SlotServiceImpl implements SlotService {
  private final SlotRepository slots;
  private final ProviderRepository providers;
  private final AppointmentRepository appointments;
  private final ApplicationEventPublisher events;

  @Autowired
  public SlotServiceImpl(SlotRepository s, ProviderRepository p, AppointmentRepository appointments,
      ApplicationEventPublisher events) {
    slots = s;
    providers = p;
    this.appointments = appointments;
    this.events = events;
  }

  @Override
  public List<Slot> findAll() {
    return slots.findAll();
  }

  @Override
  public Slot create(String pid, LocalDateTime start, int durationMinutes, String room, String equipment) {
    var slot = new Slot("SLT-" + System.currentTimeMillis(), providers.findById(pid).orElseThrow(), start, durationMinutes);
    slot.setRoom(room); slot.setEquipment(equipment); return slots.save(slot);
  }

  @Transactional
  @Override
  public Slot cancel(String id) {
    var s = slots.findById(id).orElseThrow();
    s.cancel();
    var saved = slots.save(s);
    appointments.findBySlotId(id).filter(a -> a.getStatus() == AppointmentStatus.CONFIRMED).ifPresent(a -> {
      a.markPending();
      appointments.save(a);
      events.publishEvent(new DisruptionEvent(a.getId(), TriggerReason.SLOT_CANCELLED));
    });
    return saved;
  }

  @Override
  public List<Slot> findAvailable() {
    return slots.findByStatusAndProviderStatus(SlotStatus.OPEN, ProviderStatus.AVAILABLE);
  }

  @Override
  public Slot findById(String id) {
    return slots.findById(id).orElseThrow();
  }
}
