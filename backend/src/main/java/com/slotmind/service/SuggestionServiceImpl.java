package com.slotmind.service;

import com.slotmind.domain.enums.AppointmentStatus;
import com.slotmind.domain.enums.ProviderStatus;
import com.slotmind.domain.enums.SlotStatus;
import com.slotmind.domain.enums.SuggestionStatus;
import com.slotmind.domain.enums.TriggerReason;
import com.slotmind.domain.model.RescheduleSuggestion;
import com.slotmind.repository.AppointmentRepository;
import com.slotmind.repository.ProviderRepository;
import com.slotmind.repository.SlotRepository;
import com.slotmind.repository.SuggestionRepository;
import java.util.List;
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
    suggestions = s;
    appointments = a;
    slots = sl;
    providers = p;
  }

  @Override
  public List<RescheduleSuggestion> findForAppointment(String id) {
    return suggestions.findByAppointmentId(id).stream()
        .filter(s -> s.getStatus() != SuggestionStatus.PENDING
            || (s.getSlot().getStatus() == SlotStatus.OPEN
                && s.getSlot().getProvider().getStatus() == ProviderStatus.AVAILABLE))
        .toList();
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
  public RescheduleSuggestion create(
      String aid, String sid, TriggerReason r, double c, String e, String p,
      String alternateProviderId) {
    var slot = slots.findById(sid).orElseThrow();
    var appointment = appointments.findById(aid).orElseThrow();
    if (appointment.getStatus() != AppointmentStatus.RESCHEDULE_PENDING
        || slot.getStatus() != SlotStatus.OPEN
        || slot.getProvider().getStatus() != ProviderStatus.AVAILABLE) {
      throw new IllegalStateException("Suggestion candidate is no longer available");
    }
    var existing = suggestions.findByAppointmentId(aid).stream()
        .filter(x -> x.getStatus() == SuggestionStatus.PENDING && x.getReason() == r)
        .findFirst();
    if (existing.isPresent()) return existing.get();
    return suggestions.save(
            new RescheduleSuggestion(
                "SUG-" + System.currentTimeMillis(),
            appointment,
            slot,
                alternateProviderId == null ? null : providers.findById(alternateProviderId).orElseThrow(
                    () -> new IllegalArgumentException("Unknown alternate provider")),
            r,
            c,
            e,
            p));
  }
}
