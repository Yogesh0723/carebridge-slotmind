package com.slotmind.controller;

import com.slotmind.domain.enums.SlotStatus;
import com.slotmind.domain.model.Slot;
import com.slotmind.dto.SlotRequest;
import com.slotmind.dto.SlotResponse;
import com.slotmind.service.SlotService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/slots", "/slots"})
public class SlotController {
  private final SlotService slotService;

  public SlotController(SlotService slotService) {
    this.slotService = slotService;
  }

  @GetMapping
  public List<SlotResponse> getAllSlots() {
    return slotService.findAll().stream().map(SlotResponse::from).toList();
  }

  @GetMapping("/available")
  public List<SlotResponse> getAvailableSlots() {
    return slotService.findAvailable().stream().map(SlotResponse::from).toList();
  }

  @PostMapping
  public SlotResponse createSlot(@RequestBody SlotRequest request) {
    return SlotResponse.from(slotService.create(
        request.providerId(), request.startTime(), request.durationMinutes(),
        request.room(), request.equipment()));
  }

  @DeleteMapping("/{id}")
  public void cancelSlot(@PathVariable String id) {
    slotService.cancel(id);
  }

  @PatchMapping("/{id}/status")
  public SlotResponse updateSlotStatus(
      @PathVariable String id, @RequestParam SlotStatus value) {
    Slot slot = value == SlotStatus.CANCELLED
        ? slotService.cancel(id)
        : slotService.findById(id);
    return SlotResponse.from(slot);
  }

  @PatchMapping("/{id}")
  public SlotResponse updateSlotDirect(
      @PathVariable String id,
      @RequestParam(required = false) SlotStatus status,
      @RequestBody(required = false) Map<String, Object> body) {
    SlotStatus target = status;
    if (target == null && body != null && body.containsKey("status")) {
      target = SlotStatus.valueOf(String.valueOf(body.get("status")).toUpperCase());
    }
    Slot slot = (target == SlotStatus.CANCELLED)
        ? slotService.cancel(id)
        : slotService.findById(id);
    return SlotResponse.from(slot);
  }
}
