package com.slotmind.repository;

import com.slotmind.domain.enums.ProviderStatus;
import com.slotmind.domain.enums.SlotStatus;
import com.slotmind.domain.model.Slot;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlotRepository extends JpaRepository<Slot, String> {
  List<Slot> findByStatusAndProviderStatus(SlotStatus status, ProviderStatus providerStatus);

  List<Slot> findByProviderId(String providerId);
}
