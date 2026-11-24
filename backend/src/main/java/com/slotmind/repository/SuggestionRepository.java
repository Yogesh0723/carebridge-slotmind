package com.slotmind.repository;

import com.slotmind.domain.model.RescheduleSuggestion;
import com.slotmind.domain.enums.SuggestionStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SuggestionRepository extends JpaRepository<RescheduleSuggestion, String> {
  Optional<RescheduleSuggestion> findByAppointmentIdAndSlotId(String appointmentId, String slotId);

  List<RescheduleSuggestion> findByAppointmentId(String appointmentId);

  List<RescheduleSuggestion> findByAppointmentIdAndStatusNot(
      String appointmentId, SuggestionStatus status);

  List<RescheduleSuggestion> findByAppointmentIdAndStatus(
      String appointmentId, SuggestionStatus status);
}
