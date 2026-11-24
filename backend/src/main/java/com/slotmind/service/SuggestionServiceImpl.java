package com.slotmind.service;

import com.slotmind.domain.enums.AppointmentStatus;
import com.slotmind.domain.enums.ProviderStatus;
import com.slotmind.domain.enums.SlotStatus;
import com.slotmind.domain.enums.SuggestionStatus;
import com.slotmind.domain.enums.TriggerReason;
import com.slotmind.domain.model.Provider;
import com.slotmind.domain.model.RescheduleSuggestion;
import com.slotmind.repository.AppointmentRepository;
import com.slotmind.repository.ProviderRepository;
import com.slotmind.repository.SlotRepository;
import com.slotmind.repository.SuggestionRepository;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SuggestionServiceImpl implements SuggestionService {
  private final SuggestionRepository suggestions;
  private final AppointmentRepository appointments;
  private final SlotRepository slots;
  private final ProviderRepository providers;

  @Autowired
  public SuggestionServiceImpl(SuggestionRepository s, AppointmentRepository a, SlotRepository sl,
      ProviderRepository p) {
    this.suggestions = s;
    this.appointments = a;
    this.slots = sl;
    this.providers = p;
  }

  @Override
  @Transactional(readOnly = true)
  public List<RescheduleSuggestion> findForAppointment(String id) {
    List<RescheduleSuggestion> resolved = suggestions
        .findByAppointmentIdAndStatusNot(id, SuggestionStatus.PENDING);
    List<RescheduleSuggestion> pending = suggestions
        .findByAppointmentIdAndStatus(id, SuggestionStatus.PENDING)
        .stream()
        .filter(suggestion -> suggestion.getSlot().getStatus() == SlotStatus.OPEN
            && suggestion.getSlot().getProvider().getStatus() == ProviderStatus.AVAILABLE)
        .toList();
    return Stream.concat(resolved.stream(), pending.stream()).toList();
  }

  @Transactional
  @Override
  public void clearPendingForAppointment(String appointmentId) {
    suggestions.findByAppointmentId(appointmentId).stream()
        .filter(s -> s.getStatus() == SuggestionStatus.PENDING)
        .forEach(RescheduleSuggestion::reject);
  }

  @Transactional
  @Override
  public RescheduleSuggestion accept(String id) {
    var s = suggestions.findById(id).orElseThrow();
    if (s.getStatus() != SuggestionStatus.PENDING)
      throw new IllegalStateException("Suggestion already resolved");
    if (s.getAppointment().getStatus() != AppointmentStatus.RESCHEDULE_PENDING)
      throw new IllegalStateException("Appointment is no longer waiting for rescheduling");
    if (s.getSlot().getStatus() != SlotStatus.OPEN)
      throw new IllegalStateException("Replacement slot is no longer available");
    var oldSlot = s.getAppointment().getSlot();
    s.getSlot().book();
    s.getAppointment().reschedule(s.getSlot());
    oldSlot.release();
    slots.save(oldSlot);
    s.accept();
    suggestions.findByAppointmentId(s.getAppointment().getId()).stream()
        .filter(other -> other != s && other.getStatus() == SuggestionStatus.PENDING)
        .forEach(RescheduleSuggestion::reject);
    return suggestions.save(s);
  }

  @Transactional
  @Override
  public RescheduleSuggestion reject(String id) {
    var s = suggestions.findById(id).orElseThrow();
    s.reject();
    return suggestions.save(s);
  }

  @Override
  @Transactional
  public RescheduleSuggestion create(
      String aid, String sid, TriggerReason r, double c, String e, String p,
      String alternateProviderId) {
    return create(aid, sid, r, 1, c, e, p, alternateProviderId);
  }

  @Override
  @Transactional
  public RescheduleSuggestion create(
      String aid, String sid, TriggerReason r, int rank, double c, String e, String p,
      String alternateProviderId) {
    var slot = slots.findById(sid).orElseThrow();
    var appointment = appointments.findById(aid).orElseThrow();
    if (appointment.getStatus() != AppointmentStatus.RESCHEDULE_PENDING
        || slot.getStatus() != SlotStatus.OPEN
        || slot.getProvider().getStatus() != ProviderStatus.AVAILABLE) {
      throw new IllegalStateException("Suggestion candidate is no longer available");
    }

    // Check if an active PENDING suggestion for this slot already exists
    List<RescheduleSuggestion> existing = suggestions.findByAppointmentIdAndSlotId(aid, sid);
    for (RescheduleSuggestion item : existing) {
      if (item.getStatus() == SuggestionStatus.PENDING) {
        return item;
      }
    }

    Provider altProvider = null;
    if (alternateProviderId != null && !alternateProviderId.isBlank()) {
      altProvider = providers.findById(alternateProviderId)
          .orElseThrow(() -> new IllegalStateException("Alternate provider not found"));
      if (!altProvider.getId().equals(slot.getProvider().getId())) {
        throw new IllegalStateException("Alternate provider does not own selected slot");
      }
    }

    return suggestions.save(
        new RescheduleSuggestion(
            "SUG-" + System.currentTimeMillis() + "-" + (existing.size() + 1),
            appointment, slot, altProvider, r, Math.max(1, rank), c, e, p));
  }
}
