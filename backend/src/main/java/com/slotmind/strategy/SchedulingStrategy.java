package com.slotmind.strategy;

import com.slotmind.domain.enums.*;
import com.slotmind.domain.model.*;
import java.util.*;

public interface SchedulingStrategy {
  List<Recommendation> recommend(Appointment appointment, TriggerReason reason, List<Slot> slots);

  record Recommendation(Slot slot, double confidence, String explanation, String patientScript) {}
}
