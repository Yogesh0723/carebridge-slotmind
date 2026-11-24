package com.slotmind.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.slotmind.domain.enums.AppointmentStatus;
import com.slotmind.domain.enums.AppointmentType;
import com.slotmind.domain.enums.ProviderSpecialty;
import com.slotmind.domain.enums.ProviderStatus;
import com.slotmind.domain.event.DisruptionEvent;
import com.slotmind.domain.model.Appointment;
import com.slotmind.domain.model.Provider;
import com.slotmind.domain.model.Slot;
import com.slotmind.repository.AppointmentRepository;
import com.slotmind.repository.ProviderRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class ProviderServiceImplTest {
  private ProviderRepository providers;
  private AppointmentRepository appointments;
  private ApplicationEventPublisher events;
  private SuggestionService suggestions;
  private ProviderServiceImpl service;

  @BeforeEach
  void setUp() {
    providers = org.mockito.Mockito.mock(ProviderRepository.class);
    appointments = org.mockito.Mockito.mock(AppointmentRepository.class);
    events = org.mockito.Mockito.mock(ApplicationEventPublisher.class);
    suggestions = org.mockito.Mockito.mock(SuggestionService.class);
    service = new ProviderServiceImpl(providers, appointments, events, suggestions);
  }

  @Test
  void providerAvailabilityRetriesOtherPendingAppointments() {
    Provider returningProvider = new Provider("PRV-004", "Dr. Rahul Verma", ProviderSpecialty.GENERAL);
    returningProvider.changeStatus(ProviderStatus.OFFLINE);
    Provider disruptedProvider = new Provider("PRV-001", "Dr. Sarah Chen", ProviderSpecialty.GENERAL);
    disruptedProvider.changeStatus(ProviderStatus.OFFLINE);

    Slot originalSlot = new Slot("SLT-001", disruptedProvider, LocalDateTime.now().plusHours(1), 30);
    Appointment pending = new Appointment("APT-001", "Patient", "contact", AppointmentType.ROUTINE,
        disruptedProvider, originalSlot);
    pending.markPending();

    when(providers.findById("PRV-004")).thenReturn(Optional.of(returningProvider));
    when(providers.save(returningProvider)).thenReturn(returningProvider);
    when(appointments.findByProviderIdAndStatus("PRV-004", AppointmentStatus.RESCHEDULE_PENDING))
        .thenReturn(List.of());
    when(appointments.findByStatus(AppointmentStatus.RESCHEDULE_PENDING)).thenReturn(List.of(pending));

    service.updateStatus("PRV-004", ProviderStatus.AVAILABLE);

    verify(events).publishEvent(new DisruptionEvent("APT-001", com.slotmind.domain.enums.TriggerReason.MANUAL));
  }
}