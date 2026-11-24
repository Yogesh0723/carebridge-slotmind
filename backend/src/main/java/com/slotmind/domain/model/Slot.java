package com.slotmind.domain.model;

import com.slotmind.domain.enums.SlotStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(name = "slots")
public class Slot {
  @Id
  private String id;

  @ManyToOne(optional = false)
  private Provider provider;

  @Column(name = "start_time", nullable = false)
  private LocalDateTime startTime;
  @Column(name = "duration_minutes", nullable = false)
  private int durationMinutes;

  @Enumerated(EnumType.STRING)
  private SlotStatus status = SlotStatus.OPEN;

  @Version private long version;
  private String room;
  private String equipment;

  protected Slot() {}

  public Slot(Provider p, LocalDateTime start, LocalDateTime end) {
    provider = p;
    startTime = start;
    durationMinutes = (int) Duration.between(start, end).toMinutes();
  }

  public Slot(String id, Provider p, LocalDateTime start, int durationMinutes) {
    this.id = id;
    provider = p;
    startTime = start;
    this.durationMinutes = durationMinutes;
  }

  public String getRoom() { return room; }
  public String getEquipment() { return equipment; }
  public void setRoom(String value) { room = value; }
  public void setEquipment(String value) { equipment = value; }

  public String getId() {
    return id;
  }

  public Provider getProvider() {
    return provider;
  }

  public LocalDateTime getStartTime() {
    return startTime;
  }

  public int getDurationMinutes() {
    return durationMinutes;
  }

  public SlotStatus getStatus() {
    return status;
  }

  public void book() {
    if (status != SlotStatus.OPEN) throw new IllegalStateException("Slot is not open");
    status = SlotStatus.BOOKED;
  }

  public void cancel() {
    status = SlotStatus.CANCELLED;
  }

  public void release() {
    if (status == SlotStatus.BOOKED) status = SlotStatus.OPEN;
  }
}
