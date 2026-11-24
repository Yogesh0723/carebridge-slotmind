package com.slotmind.service;

import com.slotmind.domain.enums.ProviderSpecialty;
import com.slotmind.domain.enums.ProviderStatus;
import com.slotmind.domain.model.Provider;
import java.util.List;

public interface ProviderService {
  List<Provider> findAll();
  Provider create(String name, ProviderSpecialty specialty);
  Provider updateStatus(String id, ProviderStatus status);
}