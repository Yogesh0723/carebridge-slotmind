package com.slotmind.agent;

import com.slotmind.domain.enums.AppointmentStatus;
import com.slotmind.domain.enums.ProviderStatus;
import com.slotmind.domain.enums.SlotStatus;
import com.slotmind.domain.enums.TriggerReason;
import com.slotmind.domain.event.DisruptionEvent;
import com.slotmind.domain.model.Appointment;
import com.slotmind.domain.model.Slot;
import com.slotmind.repository.AppointmentRepository;
import com.slotmind.repository.SlotRepository;
import com.slotmind.service.SuggestionService;
import com.slotmind.strategy.Recommendation;
import com.slotmind.strategy.SchedulingStrategy;
import java.util.List;
import org.springframework.scheduling.annotation.Async;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Asynchronous agentic listener that reacts to appointment disruption events after transaction commit.
 * Evaluates scheduling policies and creates actionable reschedule suggestions without blocking clinic operations.
 */
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
    this.appointments = a;
    this.slots = s;
    this.strategy = st;
    this.suggestions = ss;
  }

  @Async("taskExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handle(DisruptionEvent e) {
    try {
      var optionalAppointment = appointments.findById(e.appointmentId());
      if (optionalAppointment.isEmpty()) {
        log.warn("Disruption event received for non-existent appointment {}", e.appointmentId());
        return;
      }

      Appointment a = optionalAppointment.get();
      // Ensure the appointment is still pending reschedule
      if (a.getStatus() != AppointmentStatus.RESCHEDULE_PENDING) {
        log.debug("Skipping reschedule event for {} because status is {} (not RESCHEDULE_PENDING)", e.appointmentId(), a.getStatus());
        return;
      }

      // If the disruption was caused by provider offline, skip only if that specific provider has recovered
      if (e.reason() == TriggerReason.PROVIDER_OFFLINE && a.getProvider().getStatus() == ProviderStatus.AVAILABLE) {
        log.debug("Skipping reschedule event for {} because provider {} is back AVAILABLE", e.appointmentId(), a.getProvider().getId());
        return;
      }

      log.info("Agent processing {} disruption for appointment {} ({})", e.reason(), e.appointmentId(), a.getType());

      List<Slot> availableCandidateSlots = slots.findByStatusAndProviderStatus(
          SlotStatus.OPEN,
          ProviderStatus.AVAILABLE
      );

      List<Recommendation> recommendations = strategy.recommend(a, e.reason(), availableCandidateSlots);
      if (recommendations == null || recommendations.isEmpty()) {
        log.warn("No replacement recommendations could be generated for appointment {}", e.appointmentId());
        return;
      }

      int createdCount = 0;
      for (Recommendation r : recommendations) {
        try {
          suggestions.create(
              a.getId(),
              r.slot().getId(),
              e.reason(),
              r.confidence(),
              r.explanation(),
              r.patientScript(),
              r.alternateProviderId()
          );
          createdCount++;
        } catch (RuntimeException candidateFailure) {
          log.warn("Skipping candidate slot {} for appointment {}: {}",
              r.slot().getId(), e.appointmentId(), candidateFailure.getMessage());
        }
      }
      log.info("Agent successfully created {} suggestions for appointment {}", createdCount, e.appointmentId());
    } catch (Exception ex) {
      log.error("Failed to process reschedule agent loop for appointment {}", e.appointmentId(), ex);
    }
  }
}
