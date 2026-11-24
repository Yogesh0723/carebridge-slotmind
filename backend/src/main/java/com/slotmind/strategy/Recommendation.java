package com.slotmind.strategy;

import com.slotmind.domain.model.Slot;

public record Recommendation(
    Slot slot,
    int rank,
    double confidence,
    String explanation,
    String patientScript,
    String alternateProviderId) {
  public Recommendation(Slot slot, double confidence, String explanation, String patientScript,
      String alternateProviderId) {
    this(slot, 1, confidence, explanation, patientScript, alternateProviderId);
  }

  public Recommendation(Slot slot, double confidence, String explanation, String patientScript) {
    this(slot, 1, confidence, explanation, patientScript, null);
  }

  public Recommendation(Slot slot, int rank, double confidence, String explanation, String patientScript) {
    this(slot, rank, confidence, explanation, patientScript, null);
  }
}