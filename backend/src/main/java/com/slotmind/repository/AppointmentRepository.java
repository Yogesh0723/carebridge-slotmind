package com.slotmind.repository;

import com.slotmind.domain.enums.AppointmentStatus;
import com.slotmind.domain.model.Appointment;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentRepository extends JpaRepository<Appointment, String> {
  List<Appointment> findByStatus(AppointmentStatus status);
  List<Appointment> findByProviderIdAndStatus(String providerId, AppointmentStatus status);
  Optional<Appointment> findBySlotId(String slotId);
  long countByProviderIdAndSlotStartTimeBetween(String providerId, LocalDateTime start, LocalDateTime end);
}
