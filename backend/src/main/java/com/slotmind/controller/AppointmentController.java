package com.slotmind.controller;

import com.slotmind.domain.enums.AppointmentStatus;
import com.slotmind.domain.enums.TriggerReason;
import com.slotmind.dto.AppointmentRequest;
import com.slotmind.dto.AppointmentResponse;
import com.slotmind.dto.SuggestionResponse;
import com.slotmind.service.AppointmentService;
import com.slotmind.service.SuggestionService;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {
  private final AppointmentService appointmentService;
  private final SuggestionService suggestionService;

  public AppointmentController(
      AppointmentService appointmentService, SuggestionService suggestionService) {
    this.appointmentService = appointmentService;
    this.suggestionService = suggestionService;
  }

  @GetMapping
  public List<AppointmentResponse> getAppointments(
      @RequestParam(required = false) AppointmentStatus status) {
    return appointmentService.findAll().stream()
        .filter(appointment -> status == null || appointment.getStatus() == status)
        .map(AppointmentResponse::from)
        .toList();
  }

  @PostMapping
  public AppointmentResponse createAppointment(@RequestBody AppointmentRequest request) {
    return AppointmentResponse.from(appointmentService.create(
        request.patientName(), request.patientContact(), request.type(), request.providerId(),
        request.slotId(), request.roomRequirement(), request.equipmentRequirement(),
        request.patientPreference()));
  }

  @PostMapping("/{id}/cancel")
  public AppointmentResponse cancelAppointment(@PathVariable String id) {
    return AppointmentResponse.from(appointmentService.cancel(id));
  }

  @PostMapping("/{id}/disrupt")
  public AppointmentResponse disruptAppointment(
      @PathVariable String id, @RequestParam(defaultValue = "MANUAL") TriggerReason reason) {
    return AppointmentResponse.from(appointmentService.disrupt(id, reason));
  }

  @PostMapping("/{id}/suggest")
  public AppointmentResponse generateSuggestions(@PathVariable String id) {
    return AppointmentResponse.from(appointmentService.disrupt(id, TriggerReason.INITIAL));
  }

  @GetMapping("/{id}/suggestions")
  public List<SuggestionResponse> getSuggestions(@PathVariable String id) {
    return suggestionService.findForAppointment(id).stream().map(SuggestionResponse::from).toList();
  }

  @GetMapping(value = "/{id}/suggest/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter streamSuggestions(@PathVariable String id) {
    SseEmitter emitter = new SseEmitter(120000L);
    try {
      emitter.send(SseEmitter.event().name("reasoning").data(
          "SlotMind is evaluating provider specialty, time proximity, and patient continuity..."));
      emitter.send(SseEmitter.event().name("complete").data(
          suggestionService.findForAppointment(id).stream().map(SuggestionResponse::from).toList()));
      emitter.complete();
    } catch (Exception exception) {
      emitter.completeWithError(exception);
    }
    return emitter;
  }
}