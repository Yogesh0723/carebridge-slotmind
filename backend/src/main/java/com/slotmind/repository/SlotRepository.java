package com.slotmind.repository;

import com.slotmind.domain.enums.*;
import com.slotmind.domain.model.Slot;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlotRepository extends JpaRepository<Slot, String> {
  List<Slot> findByStatusAndProviderStatus(SlotStatus status, ProviderStatus providerStatus);

  List<Slot> findByProviderId(String providerId);
}
