package com.slotmind.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.slotmind.domain.enums.ProviderSpecialty;
import org.junit.jupiter.api.Test;

class ProviderTest {
  @Test
  void exposesAndUpdatesDailyAppointmentLimit() {
    Provider provider = new Provider("PRV-001", "Dr. Test", ProviderSpecialty.GENERAL);

    assertEquals(20, provider.getMaxDailyAppointments());
    provider.setMaxDailyAppointments(8);
    assertEquals(8, provider.getMaxDailyAppointments());
  }
}