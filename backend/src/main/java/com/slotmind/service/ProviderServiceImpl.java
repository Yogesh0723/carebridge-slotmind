package com.slotmind.service;

import com.slotmind.domain.enums.AppointmentStatus;
import com.slotmind.domain.enums.ProviderSpecialty;
import com.slotmind.domain.enums.ProviderStatus;
import com.slotmind.domain.enums.TriggerReason;
import com.slotmind.domain.event.DisruptionEvent;
import com.slotmind.domain.model.Appointment;
import com.slotmind.domain.model.Provider;
import com.slotmind.repository.AppointmentRepository;
import com.slotmind.repository.ProviderRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ProviderServiceImpl implements ProviderService {
  private static final Logger log = LoggerFactory.getLogger(ProviderServiceImpl.class);
  private final ProviderRepository repo;
  private final AppointmentRepository appointments;
  private final ApplicationEventPublisher events;
  private final SuggestionService suggestionService;

  @Autowired
  public ProviderServiceImpl(ProviderRepository r, AppointmentRepository appointments,
      ApplicationEventPublisher events, SuggestionService suggestionService) {
    this.repo = r;
    this.appointments = appointments;
    this.events = events;
    this.suggestionService = suggestionService;
  }

  @Override
  @Transactional(readOnly = true)
  public List<Provider> findAll() {
    return repo.findAll();
  }

  @Override
  public Provider create(String n, ProviderSpecialty s) {
    return repo.save(new Provider("PRV-" + System.currentTimeMillis(), n, s));
  }

  @Transactional
  @Override
  public Provider updateStatus(String id, ProviderStatus s) {
    var p = repo.findById(id).orElseThrow();
    p.changeStatus(s);
    var saved = repo.save(p);
    log.debug("Provider {} changed status to {}", id, s);
    if (s == ProviderStatus.OFFLINE) {
      List<Appointment> affected = new ArrayList<>(
          appointments.findByProviderIdAndStatus(id, AppointmentStatus.CONFIRMED));
      // Prioritize clinical urgency: URGENT first, then PROCEDURE, CONSULTATION, FOLLOW_UP, ROUTINE
      affected.sort(Comparator.comparingInt(a -> switch (a.getType()) {
        case URGENT -> 1;
        case PROCEDURE -> 2;
        case CONSULTATION -> 3;
        case FOLLOW_UP -> 4;
        case ROUTINE -> 5;
      }));
      affected.forEach(a -> {
        a.markPending();
        appointments.save(a);
        events.publishEvent(new DisruptionEvent(a.getId(), TriggerReason.PROVIDER_OFFLINE));
      });
    } else if (s == ProviderStatus.AVAILABLE) {
      appointments.findByProviderIdAndStatus(id, AppointmentStatus.RESCHEDULE_PENDING).forEach(a -> {
        a.restoreConfirmed();
        appointments.save(a);
        suggestionService.clearPendingForAppointment(a.getId());
        log.debug("Provider {} recovered; restored appointment {} and cleared pending suggestions", id, a.getId());
      });

      // A newly available provider can satisfy appointments that were left pending
      // after an earlier agent pass found no replacement. Retry those appointments
      // after commit without using PROVIDER_OFFLINE, because their original provider
      // may still be offline and the listener must evaluate the newly available pool.
      appointments.findByStatus(AppointmentStatus.RESCHEDULE_PENDING).stream()
          .filter(a -> !a.getProvider().getId().equals(id))
          .forEach(a -> {
            events.publishEvent(new DisruptionEvent(a.getId(), TriggerReason.MANUAL));
            log.debug("Provider {} became available; retriggering reschedule for appointment {}", id, a.getId());
          });
    }
    return saved;
  }
}
