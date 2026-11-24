package com.slotmind.domain.model;

import com.slotmind.domain.enums.SuggestionStatus;
import com.slotmind.domain.enums.TriggerReason;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;

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

  private LocalDateTime createdAt;

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
    createdAt = LocalDateTime.now();
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
  public LocalDateTime getCreatedAt() { return createdAt; }

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
