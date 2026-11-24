package com.slotmind.service;

import com.slotmind.domain.enums.AppointmentStatus;
import com.slotmind.domain.enums.ProviderSpecialty;
import com.slotmind.domain.enums.ProviderStatus;
import com.slotmind.domain.enums.TriggerReason;
import com.slotmind.domain.event.DisruptionEvent;
import com.slotmind.domain.model.Provider;
import com.slotmind.repository.AppointmentRepository;
import com.slotmind.repository.ProviderRepository;
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
    repo = r;
    this.appointments = appointments;
    this.events = events;
    this.suggestionService = suggestionService;
  }

  @Override
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
      appointments.findByProviderIdAndStatus(id, AppointmentStatus.CONFIRMED).forEach(a -> {
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
    }
    return saved;
  }
}
