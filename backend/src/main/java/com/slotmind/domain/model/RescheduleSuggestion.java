package com.slotmind.domain.model;

import com.slotmind.domain.enums.*;
import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(
    name = "suggestions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"appointment_id", "slot_id"}))
public class RescheduleSuggestion {
  @Id
  private String id;

  @ManyToOne(optional = false)
  private Appointment appointment;

  @ManyToOne(optional = false)
  private Slot slot;

  @ManyToOne
  private Provider alternateProvider;

  private java.time.LocalDateTime createdAt;

  @Enumerated(EnumType.STRING)
  private SuggestionStatus status = SuggestionStatus.PENDING;

  @Enumerated(EnumType.STRING)
  private TriggerReason reason;

  private double confidence;
  private String explanation;
  private String patientScript;
  @Version private long version;

  protected RescheduleSuggestion() {}

  public RescheduleSuggestion(
      Appointment a, Slot s, Provider alternate, TriggerReason r, double c, String e, String script) {
    appointment = a;
    slot = s;
    alternateProvider = alternate;
    reason = r;
    confidence = c;
    explanation = e;
    patientScript = script;
    createdAt = java.time.LocalDateTime.now();
  }

  public RescheduleSuggestion(String id, Appointment a, Slot s, Provider alternate, TriggerReason r, double c, String e, String script) {
    this(a, s, alternate, r, c, e, script);
    this.id = id;
  }

  public String getId() {
    return id;
  }

  public Appointment getAppointment() {
    return appointment;
  }

  public Slot getSlot() {
    return slot;
  }

  public Provider getAlternateProvider() { return alternateProvider; }
  public java.time.LocalDateTime getCreatedAt() { return createdAt; }

  public SuggestionStatus getStatus() {
    return status;
  }

  public TriggerReason getReason() {
    return reason;
  }

  public double getConfidence() {
    return confidence;
  }

  public String getExplanation() {
    return explanation;
  }

  public String getPatientScript() {
    return patientScript;
  }

  public void accept() {
    if (status != SuggestionStatus.PENDING)
      throw new IllegalStateException("Suggestion already resolved");
    status = SuggestionStatus.ACCEPTED;
  }

  public void reject() {
    if (status != SuggestionStatus.PENDING)
      throw new IllegalStateException("Suggestion already resolved");
    status = SuggestionStatus.REJECTED;
  }
}
