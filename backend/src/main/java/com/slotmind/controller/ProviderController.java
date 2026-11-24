package com.slotmind.controller;

import com.slotmind.domain.enums.ProviderStatus;
import com.slotmind.domain.model.Provider;
import com.slotmind.dto.ProviderRequest;
import com.slotmind.service.ProviderService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/providers", "/providers"})
public class ProviderController {
  private final ProviderService providerService;

  public ProviderController(ProviderService providerService) {
    this.providerService = providerService;
  }

  @GetMapping
  public List<Provider> getAllProviders() {
    return providerService.findAll();
  }

  @PostMapping
  public Provider createProvider(@RequestBody ProviderRequest request) {
    return providerService.create(request.name(), request.specialty());
  }

  @PatchMapping("/{id}/status")
  public Provider updateProviderStatusParam(
      @PathVariable String id, @RequestParam ProviderStatus value) {
    return providerService.updateStatus(id, value);
  }

  @PatchMapping("/{id}")
  public Provider updateProviderStatusDirect(
      @PathVariable String id,
      @RequestParam(required = false) ProviderStatus status,
      @RequestBody(required = false) Map<String, Object> body) {
    ProviderStatus targetStatus = status;
    if (targetStatus == null && body != null && body.containsKey("status")) {
      targetStatus = ProviderStatus.valueOf(String.valueOf(body.get("status")).toUpperCase());
    }
    if (targetStatus == null) {
      targetStatus = ProviderStatus.AVAILABLE;
    }
    return providerService.updateStatus(id, targetStatus);
  }
}
