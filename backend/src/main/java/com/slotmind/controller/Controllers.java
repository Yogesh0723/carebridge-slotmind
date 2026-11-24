package com.slotmind.controller;

import com.slotmind.domain.enums.*;
import com.slotmind.domain.model.Provider;
import com.slotmind.dto.Dtos.*;
import com.slotmind.service.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/providers")
class ProviderController {
  private final ProviderService s;

  @Autowired
  ProviderController(ProviderService s) {
    this.s = s;
  }

  @GetMapping
  List<Provider> all() {
    return s.all();
  }

  @PostMapping
  Provider create(@RequestBody ProviderRequest r) {
    return s.create(r.name(), r.specialty());
  }

  @PatchMapping("/{id}/status")
  Provider status(@PathVariable String id, @RequestParam ProviderStatus value) {
    return s.status(id, value);
  }
}

@RestController
@RequestMapping("/api/slots")
class SlotController {
  private final SlotService s;

  @Autowired
  SlotController(SlotService s) {
    this.s = s;
  }

  @GetMapping
  List<SlotResponse> all() {
    return s.all().stream().map(SlotController::response).toList();
  }

  @GetMapping("/available")
  List<SlotResponse> available() {
    return s.available().stream().map(SlotController::response).toList();
  }

  @PostMapping
  SlotResponse create(@RequestBody SlotRequest r) {
    var x = s.create(r.providerId(), r.startTime(), r.durationMinutes(), r.room(), r.equipment());
    return response(x);
  }

  @DeleteMapping("/{id}")
  void cancel(@PathVariable String id) {
    s.cancel(id);
  }

  @PatchMapping("/{id}/status")
  SlotResponse status(@PathVariable String id, @RequestParam SlotStatus value) {
    var x = value == SlotStatus.CANCELLED ? s.cancel(id) : s.all().stream().filter(slot -> slot.getId().equals(id)).findFirst().orElseThrow();
    return response(x);
  }

  private static SlotResponse response(com.slotmind.domain.model.Slot x) {
    return new SlotResponse(x.getId(), x.getStartTime(), x.getDurationMinutes(), x.getStatus(),
        x.getProvider().getId(), x.getProvider().getName(), x.getProvider().getStatus(), x.getRoom(), x.getEquipment());
  }

  record SlotResponse(
      String id,
      java.time.LocalDateTime startTime,
      int durationMinutes,
      SlotStatus status,
      String providerId,
      String providerName, ProviderStatus providerStatus, String room, String equipment) {}
}

@RestController
@RequestMapping("/api/appointments")
class AppointmentController {
  private final AppointmentService s;
  private final SuggestionService ss;

  @Autowired
  AppointmentController(AppointmentService s, SuggestionService ss) {
    this.s = s;
    this.ss = ss;
  }

  @GetMapping
  List<AppointmentResponse> all(@RequestParam(required = false) AppointmentStatus status) {
    return s.all().stream().filter(a -> status == null || a.getStatus() == status).map(AppointmentResponse::from).toList();
  }

  @PostMapping
  AppointmentResponse create(@RequestBody AppointmentRequest r) {
    return AppointmentResponse.from(
        s.create(r.patientName(), r.patientContact(), r.type(), r.providerId(), r.slotId(), r.roomRequirement(), r.equipmentRequirement(), r.patientPreference()));
  }

  @PostMapping("/{id}/cancel")
  AppointmentResponse cancel(@PathVariable String id) { return AppointmentResponse.from(s.cancel(id)); }

  @PostMapping("/{id}/disrupt")
  AppointmentResponse disrupt(
      @PathVariable String id, @RequestParam(defaultValue = "MANUAL") TriggerReason reason) {
    return AppointmentResponse.from(s.disrupt(id, reason));
  }

  @PostMapping("/{id}/suggest")
  AppointmentResponse suggest(@PathVariable String id) {
    return AppointmentResponse.from(s.disrupt(id, TriggerReason.MANUAL));
  }

  @GetMapping("/{id}/suggestions")
  List<SuggestionResponse> suggestions(@PathVariable String id) {
    return ss.forAppointment(id).stream().map(SuggestionResponse::from).toList();
  }

  @GetMapping(value = "/{id}/suggest/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  SseEmitter stream(@PathVariable String id) {
    var e = new SseEmitter(120000L);
    try {
      e.send(
          SseEmitter.event()
              .name("reasoning")
              .data(
                  "SlotMind is evaluating provider specialty, time proximity, and patient"
                      + " continuity..."));
      e.send(
          SseEmitter.event()
              .name("complete")
              .data(ss.forAppointment(id).stream().map(SuggestionResponse::from).toList()));
      e.complete();
    } catch (Exception ex) {
      e.completeWithError(ex);
    }
    return e;
  }
}

@RestController
@RequestMapping("/api/suggestions")
class SuggestionController {
  private final SuggestionService s;

  @Autowired
  SuggestionController(SuggestionService s) {
    this.s = s;
  }

  @PostMapping("/{id}/accept")
  SuggestionResponse accept(@PathVariable String id) {
    return SuggestionResponse.from(s.accept(id));
  }

  @PostMapping("/{id}/reject")
  SuggestionResponse reject(@PathVariable String id) {
    return SuggestionResponse.from(s.reject(id));
  }
}

