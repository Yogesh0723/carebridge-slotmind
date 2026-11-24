package com.slotmind.domain.model;

import com.slotmind.domain.enums.AppointmentStatus;
import com.slotmind.domain.enums.AppointmentType;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "appointments")
public class Appointment {
  @Id
  private String id;

  private String patientName;
  private String patientContact;
  private String roomRequirement;
  private String equipmentRequirement;
  private String patientPreference;
  private int durationMinutes;

  @Enumerated(EnumType.STRING)
  private AppointmentType type;

  @ManyToOne(optional = false)
  private Provider provider;

  @ManyToOne(optional = false)
  private Slot slot;

  @Enumerated(EnumType.STRING)
  private AppointmentStatus status = AppointmentStatus.CONFIRMED;

  @Version private long version;

  protected Appointment() {}

  public Appointment(String patientName, String contact, AppointmentType type, Provider p, Slot s) {
    this.patientName = patientName;
    patientContact = contact;
    this.type = type;
    provider = p;
    slot = s;
    durationMinutes = defaultDuration(type);
  }

  public Appointment(String id, String patientName, String contact, AppointmentType type, Provider p, Slot s) {
    this.id = id;
    this.patientName = patientName;
    this.patientContact = contact;
    this.type = type;
    this.provider = p;
    this.slot = s;
    this.durationMinutes = defaultDuration(type);
  }

  private static int defaultDuration(AppointmentType type) {
    return switch (type) { case URGENT, FOLLOW_UP -> 30; case CONSULTATION -> 45; case PROCEDURE -> 60; default -> 30; };
  }
  public String getRoomRequirement() { return roomRequirement; }
  public String getEquipmentRequirement() { return equipmentRequirement; }
  public String getPatientPreference() { return patientPreference; }
  public int getDurationMinutes() { return durationMinutes; }
  public void setRoomRequirement(String value) { roomRequirement = value; }
  public void setEquipmentRequirement(String value) { equipmentRequirement = value; }
  public void setPatientPreference(String value) { patientPreference = value; }
  public void setDurationMinutes(int value) { durationMinutes = value; }

  public String getId() {
    return id;
  }

  public String getPatientName() {
    return patientName;
  }

  public String getPatientContact() {
    return patientContact;
  }

  public AppointmentType getType() {
    return type;
  }

  public Provider getProvider() {
    return provider;
  }

  public Slot getSlot() {
    return slot;
  }

  public AppointmentStatus getStatus() {
    return status;
  }

  public void markPending() {
    if (status != AppointmentStatus.CONFIRMED)
      throw new IllegalStateException("Appointment cannot be disrupted");
    status = AppointmentStatus.RESCHEDULE_PENDING;
  }

  public void reschedule(Slot newSlot) {
    if (status != AppointmentStatus.RESCHEDULE_PENDING)
      throw new IllegalStateException("Suggestion is not pending");
    slot = newSlot;
    provider = newSlot.getProvider();
    status = AppointmentStatus.RESCHEDULED;
  }

  public void cancel() {
    status = AppointmentStatus.CANCELLED;
  }

  public void restoreConfirmed() {
    if (status == AppointmentStatus.RESCHEDULE_PENDING) {
      status = AppointmentStatus.CONFIRMED;
    }
  }
}
