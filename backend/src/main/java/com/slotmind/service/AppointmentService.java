package com.slotmind.service;

import com.slotmind.domain.enums.AppointmentStatus;
import com.slotmind.domain.enums.AppointmentType;
import com.slotmind.domain.enums.TriggerReason;
import com.slotmind.domain.model.Appointment;
import java.util.List;

public interface AppointmentService {
  List<Appointment> findAll();
  List<Appointment> findByStatus(AppointmentStatus status);
  Appointment create(String patientName, String patientContact, AppointmentType type,
      String providerId, String slotId, String room, String equipment, String preference);
  Appointment cancel(String id);
  Appointment complete(String id);
  Appointment disrupt(String id, TriggerReason reason);
  Appointment findById(String id);
}
