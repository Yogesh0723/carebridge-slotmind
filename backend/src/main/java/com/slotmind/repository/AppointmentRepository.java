package com.slotmind.repository;

import com.slotmind.domain.enums.*;
import com.slotmind.domain.model.Appointment;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentRepository extends JpaRepository<Appointment, String> {
  List<Appointment> findByStatus(AppointmentStatus status);
  List<Appointment> findByProviderIdAndStatus(String providerId, AppointmentStatus status);
  Optional<Appointment> findBySlotId(String slotId);
  long countByProviderIdAndSlotStartTimeBetween(String providerId, java.time.LocalDateTime start, java.time.LocalDateTime end);
}
