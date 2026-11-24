package com.slotmind.strategy;

import com.slotmind.domain.model.Slot;

public record Recommendation(
    Slot slot,
    double confidence,
    String explanation,
    String patientScript,
    String alternateProviderId) {
  public Recommendation(Slot slot, double confidence, String explanation, String patientScript) {
    this(slot, confidence, explanation, patientScript, null);
  }
}