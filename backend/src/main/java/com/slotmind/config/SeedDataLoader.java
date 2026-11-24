package com.slotmind.config;

import com.slotmind.domain.enums.AppointmentType;
import com.slotmind.domain.enums.ProviderSpecialty;
import com.slotmind.domain.enums.ProviderStatus;
import com.slotmind.domain.model.Appointment;
import com.slotmind.domain.model.Provider;
import com.slotmind.domain.model.Slot;
import com.slotmind.repository.AppointmentRepository;
import com.slotmind.repository.ProviderRepository;
import com.slotmind.repository.SlotRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SeedDataLoader {

  @Bean
  CommandLineRunner seedData(
      ProviderRepository providers, SlotRepository slots, AppointmentRepository appointments) {
    return args -> {
      if (providers.count() > 0 && slots.count() > 0 && appointments.count() > 0) {
        return;
      }

      appointments.deleteAll();
      slots.deleteAll();
      providers.deleteAll();

      Map<String, Provider> roster = new LinkedHashMap<>();
      roster.put("sarah", provider("PRV-001", "Dr. Sarah Chen", ProviderSpecialty.GENERAL, ProviderStatus.BUSY));
      roster.put("james", provider("PRV-002", "Dr. James Okonkwo", ProviderSpecialty.SPECIALIST, ProviderStatus.AVAILABLE));
      roster.put("priya", provider("PRV-003", "Dr. Priya Sharma", ProviderSpecialty.PEDIATRIC, ProviderStatus.BUSY));
      roster.put("rahul", provider("PRV-004", "Dr. Rahul Verma", ProviderSpecialty.GENERAL, ProviderStatus.AVAILABLE));
      roster.put("ananya", provider("PRV-005", "Dr. Ananya Iyer", ProviderSpecialty.SPECIALIST, ProviderStatus.BUSY));
      providers.saveAll(roster.values());

      LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
      Map<String, Slot> seededSlots = new LinkedHashMap<>();
      seededSlots.put("slt1", slot("SLT-001", roster.get("sarah"), now.plusMinutes(60), 30, true));
      seededSlots.put("slt2", slot("SLT-002", roster.get("sarah"), now.plusMinutes(120), 30, true));
      seededSlots.put("slt3", slot("SLT-003", roster.get("priya"), now.plusMinutes(90), 45, true));
      seededSlots.put("slt4", slot("SLT-004", roster.get("james"), now.plusMinutes(180), 30, false));
      seededSlots.put("slt5", slot("SLT-005", roster.get("rahul"), now.plusMinutes(150), 30, false));
      seededSlots.put("slt6", slot("SLT-006", roster.get("ananya"), now.plusMinutes(240), 60, true));
      seededSlots.put("slt7", slot("SLT-007", roster.get("james"), now.plusMinutes(300), 30, false));
      seededSlots.put("slt8", slot("SLT-008", roster.get("rahul"), now.plusMinutes(360), 30, false));
      slots.saveAll(seededSlots.values());

      appointments.save(new Appointment("APT-001", "John Martinez", "555-0101", AppointmentType.ROUTINE, roster.get("sarah"), seededSlots.get("slt1")));
      appointments.save(new Appointment("APT-002", "Emily Watson", "555-0102", AppointmentType.FOLLOW_UP, roster.get("sarah"), seededSlots.get("slt2")));
      appointments.save(new Appointment("APT-003", "Aisha Patel", "555-0103", AppointmentType.URGENT, roster.get("priya"), seededSlots.get("slt3")));
      appointments.save(new Appointment("APT-004", "Robert Kim", "555-0104", AppointmentType.ROUTINE, roster.get("ananya"), seededSlots.get("slt6")));
    };
  }

  private Provider provider(String id, String name, ProviderSpecialty specialty, ProviderStatus status) {
    Provider provider = new Provider(id, name, specialty);
    provider.changeStatus(status);
    return provider;
  }

  private Slot slot(String id, Provider provider, LocalDateTime start, int durationMinutes, boolean booked) {
    Slot slot = new Slot(id, provider, start, durationMinutes);
    if (booked) {
      slot.book();
    }
    return slot;
  }
}