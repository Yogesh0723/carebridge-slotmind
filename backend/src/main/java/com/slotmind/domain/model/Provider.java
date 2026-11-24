package com.slotmind.domain.model;

import com.slotmind.domain.enums.*;
import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name = "providers")
public class Provider {
  @Id
  private String id;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  private ProviderSpecialty specialty;

  @Enumerated(EnumType.STRING)
  private ProviderStatus status = ProviderStatus.AVAILABLE;

  @Version private long version;

  private int maxDailyAppointments = 20;

  protected Provider() {}

  public Provider(String name, ProviderSpecialty specialty) {
    this.name = name;
    this.specialty = specialty;
  }

  public Provider(String id, String name, ProviderSpecialty specialty) {
    this.id = id;
    this.name = name;
    this.specialty = specialty;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public ProviderSpecialty getSpecialty() {
    return specialty;
  }

  public ProviderStatus getStatus() {
    return status;
  }

  public long getVersion() {
    return version;
  }

  public int getMaxDailyAppointments() { return maxDailyAppointments; }
  public void setMaxDailyAppointments(int value) { maxDailyAppointments = value; }

  public void changeStatus(ProviderStatus s) {
    status = s;
  }
}
