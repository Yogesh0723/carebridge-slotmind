package com.slotmind.controller;

import com.slotmind.domain.enums.ProviderStatus;
import com.slotmind.domain.model.Provider;
import com.slotmind.dto.ProviderRequest;
import com.slotmind.service.ProviderService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/providers")
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
  public Provider updateProviderStatus(
      @PathVariable String id, @RequestParam ProviderStatus value) {
    return providerService.updateStatus(id, value);
  }
}