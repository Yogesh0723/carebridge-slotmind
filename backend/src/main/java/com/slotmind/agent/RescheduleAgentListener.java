package com.slotmind.agent;

import com.slotmind.domain.enums.AppointmentStatus;
import com.slotmind.domain.enums.ProviderStatus;
import com.slotmind.domain.enums.SlotStatus;
import com.slotmind.domain.event.*;
import com.slotmind.repository.*;
import com.slotmind.service.*;
import com.slotmind.strategy.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class RescheduleAgentListener {
  private static final Logger log = LoggerFactory.getLogger(RescheduleAgentListener.class);
  private final AppointmentRepository appointments;
  private final SlotRepository slots;
  private final SchedulingStrategy strategy;
  private final SuggestionService suggestions;

  @Autowired
  public RescheduleAgentListener(
      AppointmentRepository a, SlotRepository s, SchedulingStrategy st, SuggestionService ss) {
    appointments = a;
    slots = s;
    strategy = st;
    suggestions = ss;
  }

  @Async
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handle(DisruptionEvent e) {
    try {
      var a = appointments.findById(e.appointmentId()).orElseThrow();
      if (a.getStatus() != AppointmentStatus.RESCHEDULE_PENDING
          || a.getProvider().getStatus() == ProviderStatus.AVAILABLE) {
        log.debug("Skipping reschedule event for {} because the appointment is no longer disrupted", e.appointmentId());
        return;
      }
      log.debug("Processing {} disruption for appointment {}", e.reason(), e.appointmentId());
      strategy
        .recommend(
            a,
            e.reason(),
            slots.findByStatusAndProviderStatus(
                SlotStatus.OPEN,
                ProviderStatus.AVAILABLE))
          .forEach(
            r ->
                suggestions.create(
                    a.getId(),
                    r.slot().getId(),
                    e.reason(),
                    r.confidence(),
                    r.explanation(),
                    r.patientScript(),
                    r.alternateProviderId()));
    } catch (Exception ex) {
      log.error("Failed to create reschedule suggestions for {}", e.appointmentId(), ex);
    }
  }
}
