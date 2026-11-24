package com.slotmind.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.slotmind.domain.enums.ProviderStatus;
import com.slotmind.domain.enums.SlotStatus;
import com.slotmind.domain.enums.TriggerReason;
import com.slotmind.domain.model.Appointment;
import com.slotmind.domain.model.Slot;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.net.http.HttpTimeoutException;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI-powered scheduling strategy.
 * Builds differentiated, context-rich prompts based on disruption triggers,
 * validates LLM responses against active domain constraints, and reliably falls back to rules on any failure.
 */
public class AiSchedulingStrategy implements SchedulingStrategy {
  private static final Logger log = LoggerFactory.getLogger(AiSchedulingStrategy.class);
  private final RuleBasedSchedulingStrategy fallback = new RuleBasedSchedulingStrategy();
  private final ObjectMapper mapper = new ObjectMapper();
  private final HttpClient client;
  private final String url;
  private final String token;
  private final String model;
  private final String product;
  private final MeterRegistry meterRegistry;

  public AiSchedulingStrategy(
      @Value("${slotmind.ai.url}") String url,
      @Value("${slotmind.ai.token:}") String token,
      @Value("${slotmind.ai.model}") String model,
      @Value("${slotmind.ai.product:PC1}") String product) {
    this(url, token, model, product, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
  }

  public AiSchedulingStrategy(
      String url,
      String token,
      String model,
      String product,
      HttpClient client) {
    this(url, token, model, product, client, null);
  }

  public AiSchedulingStrategy(String url, String token, String model, String product,
      HttpClient client, MeterRegistry meterRegistry) {
    this.url = url;
    this.token = token;
    this.model = model;
    this.product = product;
    this.client = client;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public List<Recommendation> recommend(Appointment appointment, TriggerReason reason, List<Slot> slots) {
    if (token == null || token.isBlank() || slots == null || slots.isEmpty()) {
      log.warn("AI scheduling token not configured or candidate list empty; using rule-based fallback for appointment {}", appointment.getId());
      return fallback.recommend(appointment, reason, slots);
    }
    try {
      List<Slot> validCandidates = filterValidCandidates(appointment, slots);
      if (validCandidates.isEmpty()) {
        log.info("No candidates strictly matching all constraints for appointment {}; using rule-based fallback", appointment.getId());
        return fallback.recommend(appointment, reason, slots);
      }
      String prompt = buildPrompt(appointment, reason, validCandidates);
      String body = mapper.writeValueAsString(java.util.Map.of(
          "model", model,
          "messages", List.of(
              java.util.Map.of("role", "system", "content", buildSystemInstruction(reason)),
              java.util.Map.of("role", "user", "content", prompt)
          ),
          "temperature", 0.2
      ));

      HttpRequest request = HttpRequest.newBuilder(URI.create(url))
          .header("Authorization", "Bearer " + token)
          .header("Content-Type", "application/json")
          .header("product", product)
          .timeout(Duration.ofSeconds(15))
          .POST(HttpRequest.BodyPublishers.ofString(body))
          .build();

      HttpResponse<String> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
        throw new IllegalStateException("AI gateway returned HTTP status " + httpResponse.statusCode() + ": " + httpResponse.body());
      }

      JsonNode root = mapper.readTree(httpResponse.body());
      String content = root.path("choices").path(0).path("message").path("content").asText();
      if (content == null || content.isBlank()) {
        count("empty_output");
        throw new IllegalStateException("AI gateway returned empty choices");
      }

      String cleanedJson = content.replace("```json", "").replace("```", "").trim();
      JsonNode answer = mapper.readTree(cleanedJson);
      List<JsonNode> answers = answer.isArray() ? new ArrayList<>() : List.of(answer);
      if (answer.isArray()) answer.forEach(answers::add);
      if (answers.isEmpty()) {
        count("empty_output");
        throw new IllegalStateException("AI response contained no recommendations");
      }

      List<Recommendation> recommendations = new ArrayList<>();
      Set<String> recommendedSlotIds = new HashSet<>();
      for (int index = 0; index < answers.size() && recommendations.size() < 3; index++) {
        JsonNode candidateAnswer = answers.get(index);
        JsonNode slotIdNode = candidateAnswer.path("slotId");
        if (!slotIdNode.isTextual() || slotIdNode.asText().isBlank()) {
          count("invalid_json");
          continue;
        }
        String recommendedSlotId = slotIdNode.asText();
        Slot selected = validCandidates.stream()
            .filter(s -> s.getId().equals(recommendedSlotId) && s.getStatus() == SlotStatus.OPEN)
            .findFirst().orElse(null);
        if (selected == null) {
          count("invalid_slot");
          continue;
        }
        if (!recommendedSlotIds.add(recommendedSlotId)) {
          count("duplicate_slot");
          continue;
        }
        double confidence = Math.max(0.0, Math.min(1.0, candidateAnswer.path("confidence").asDouble(0.85)));
        String reasoning = candidateAnswer.path("reasoning").asText("Selected slot best matches specialty and schedule continuity.");
        String patientScript = candidateAnswer.path("patientScript").asText(
            "Hello " + appointment.getPatientName() + ", we found a replacement appointment with "
                + selected.getProvider().getName() + " on " + selected.getStartTime() + ". Would that work for you?");
        String alternateProviderId = candidateAnswer.path("alternateProviderId").isTextual()
            ? candidateAnswer.path("alternateProviderId").asText() : selected.getProvider().getId();
        recommendations.add(new Recommendation(selected, recommendations.size() + 1, confidence,
            reasoning, patientScript, alternateProviderId));
      }
      if (recommendations.isEmpty()) throw new IllegalStateException("AI returned no valid recommendations");
      log.info("AI successfully ranked {} recommendations for appointment {}", recommendations.size(), appointment.getId());
      return recommendations;
    } catch (Exception ex) {
      if (ex instanceof JsonProcessingException) count("invalid_json");
      if (isTimeout(ex)) count("timeout");
      log.warn("AI scheduling failed for appointment {}: {}; executing rule-based fallback", appointment.getId(), ex.getMessage());
      return fallback.recommend(appointment, reason, slots);
    }
  }

  private boolean isTimeout(Exception ex) {
    return ex instanceof HttpTimeoutException || ex.getCause() instanceof HttpTimeoutException
        || ex instanceof CompletionException && ex.getCause() instanceof HttpTimeoutException;
  }

  private void count(String outcome) {
    if (meterRegistry != null) {
      meterRegistry.counter("slotmind.ai.responses", "outcome", outcome).increment();
    }
  }

  private String buildSystemInstruction(TriggerReason reason) {
    if (reason == TriggerReason.PROVIDER_OFFLINE) {
      return "You are SlotMind's clinical scheduling agent. The original doctor is OFFLINE. "
          + "Your recovery goal: identify an available alternative provider with identical specialty and earliest opening. "
          + "Output ONLY a JSON array with up to 3 ranked objects, each containing slotId, alternateProviderId, confidence (0.0-1.0), reasoning, patientScript.";
    } else if (reason == TriggerReason.SLOT_CANCELLED) {
      return "You are SlotMind's clinical scheduling agent. The original time slot was CANCELLED. "
          + "Your recovery goal: find a new open slot, strictly PREFERRING the same doctor to maintain patient continuity, or same specialty if needed. "
          + "Output ONLY a JSON array with up to 3 ranked objects, each containing slotId, alternateProviderId, confidence (0.0-1.0), reasoning, patientScript.";
    } else {
      return "You are SlotMind's clinical scheduling agent assisting on-demand patient rescheduling. "
          + "Balance patient continuity with the original doctor and nearest available slot. "
          + "Output ONLY a JSON array with up to 3 ranked objects, each containing slotId, alternateProviderId, confidence (0.0-1.0), reasoning, patientScript.";
    }
  }

  private String buildPrompt(Appointment appointment, TriggerReason reason, List<Slot> slots) {
    StringBuilder sb = new StringBuilder();
    sb.append("Please recommend the best replacement slot for the following disrupted appointment:\n");
    sb.append("PATIENT DETAILS:\n");
    sb.append("- Name: ").append(appointment.getPatientName()).append("\n");
    sb.append("- Urgency / Type: ").append(appointment.getType()).append("\n");
    sb.append("- Duration Needed: ").append(appointment.getDurationMinutes()).append(" minutes\n");
    sb.append("- Original Provider: ").append(appointment.getProvider().getName())
        .append(" (ID: ").append(appointment.getProvider().getId())
        .append(", Specialty: ").append(appointment.getProvider().getSpecialty()).append(")\n");
    if (appointment.getPatientPreference() != null && !appointment.getPatientPreference().isBlank()) {
      sb.append("- Time Preference: ").append(appointment.getPatientPreference()).append("\n");
    }
    if (appointment.getRoomRequirement() != null && !appointment.getRoomRequirement().isBlank()) {
      sb.append("- Room Requirement: ").append(appointment.getRoomRequirement()).append("\n");
    }
    if (appointment.getEquipmentRequirement() != null && !appointment.getEquipmentRequirement().isBlank()) {
      sb.append("- Equipment Requirement: ").append(appointment.getEquipmentRequirement()).append("\n");
    }
    sb.append("\nDISRUPTION REASON: ").append(reason).append("\n\n");
    sb.append("AVAILABLE CANDIDATE SLOTS:\n");
    for (Slot s : slots) {
      sb.append("- Slot ID: ").append(s.getId())
          .append(" | Start: ").append(s.getStartTime())
          .append(" | Duration: ").append(s.getDurationMinutes()).append("m")
          .append(" | Provider: ").append(s.getProvider().getName())
          .append(" (ID: ").append(s.getProvider().getId())
          .append(", Specialty: ").append(s.getProvider().getSpecialty()).append(")");
      if (s.getRoom() != null) sb.append(" | Room: ").append(s.getRoom());
      if (s.getEquipment() != null) sb.append(" | Equip: ").append(s.getEquipment());
      sb.append("\n");
    }
    sb.append("\\nRespond with ONLY a valid JSON array containing up to 3 distinct objects ranked best-first. "
        + "Return as many different valid options as the candidate list supports (prefer three): "
        + "[{\\\"slotId\\\": \\\"...\\\", \\\"alternateProviderId\\\": \\\"...\\\", \\\"confidence\\\": 0.85, \\\"reasoning\\\": \\\"...\\\", \\\"patientScript\\\": \\\"...\\\"}]");
    return sb.toString();
  }

  private List<Slot> filterValidCandidates(Appointment appointment, List<Slot> slots) {
    List<Slot> sameSpecialty = slots.stream()
        .filter(slot -> slot.getStatus() == SlotStatus.OPEN)
        .filter(slot -> slot.getProvider().getStatus() == ProviderStatus.AVAILABLE)
        .filter(slot -> slot.getProvider().getSpecialty() == appointment.getProvider().getSpecialty())
        .filter(slot -> slot.getDurationMinutes() >= appointment.getDurationMinutes())
        .filter(slot -> matchesPreference(appointment.getPatientPreference(), slot))
        .toList();

    if (!sameSpecialty.isEmpty()) {
      return sameSpecialty;
    }

    // Fallback: If no provider of identical specialty is available, evaluate open slots for cross-coverage
    return slots.stream()
        .filter(slot -> slot.getStatus() == SlotStatus.OPEN)
        .filter(slot -> slot.getProvider().getStatus() == ProviderStatus.AVAILABLE)
        .filter(slot -> slot.getDurationMinutes() >= appointment.getDurationMinutes())
        .toList();
  }

  private boolean matchesPreference(String preference, Slot slot) {
    if (preference == null || preference.isBlank()) return true;
    int hour = slot.getStartTime().getHour();
    return !("MORNING".equalsIgnoreCase(preference) && hour >= 12)
        && !("AFTERNOON".equalsIgnoreCase(preference) && hour < 12);
  }
}
