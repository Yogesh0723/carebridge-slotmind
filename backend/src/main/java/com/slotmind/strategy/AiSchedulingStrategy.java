package com.slotmind.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slotmind.domain.enums.TriggerReason;
import com.slotmind.domain.model.Appointment;
import com.slotmind.domain.model.Slot;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Calls the configured gateway when credentials exist and always falls back to rules on failure. */
public class AiSchedulingStrategy implements SchedulingStrategy {
  private static final Logger log = LoggerFactory.getLogger(AiSchedulingStrategy.class);
  private final RuleBasedSchedulingStrategy fallback = new RuleBasedSchedulingStrategy();
  private final ObjectMapper mapper = new ObjectMapper();
  private final HttpClient client = HttpClient.newHttpClient();
  private final String url;
  private final String token;
  private final String model;
  private final String product;

  public AiSchedulingStrategy(
      @Value("${slotmind.ai.url}") String url,
      @Value("${slotmind.ai.token:}") String token,
      @Value("${slotmind.ai.model}") String model,
      @Value("${slotmind.ai.product:PC1}") String product) {
    this.url = url;
    this.token = token;
    this.model = model;
    this.product = product;
  }

  @Override
  public List<Recommendation> recommend(Appointment appointment, TriggerReason reason, List<Slot> slots) {
    if (token == null || token.isBlank() || slots.isEmpty()) {
      log.warn("AI scheduling unavailable; using rule-based fallback for appointment {}", appointment.getId());
      return fallback.recommend(appointment, reason, slots);
    }
    try {
      String prompt = "Return JSON {slotId,confidence,reasoning}. Appointment " + appointment.getId()
          + " provider " + appointment.getProvider().getName() + " trigger " + reason
          + " candidates " + slots.stream().map(s -> s.getId() + "@" + s.getStartTime()).toList();
      String body = mapper.writeValueAsString(java.util.Map.of(
          "model", model, "messages", List.of(java.util.Map.of("role", "user", "content", prompt))));
      HttpRequest request = HttpRequest.newBuilder(URI.create(url))
          .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
          .header("product", product)
          .POST(HttpRequest.BodyPublishers.ofString(body)).build();
      HttpResponse<String> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
        throw new IllegalStateException("AI gateway returned HTTP " + httpResponse.statusCode());
      }
      String response = httpResponse.body();
      JsonNode root = mapper.readTree(response);
      String content = root.path("choices").path(0).path("message").path("content").asText();
      JsonNode answer = mapper.readTree(content.replace("```json", "").replace("```", "").trim());
      Slot selected = slots.stream().filter(s -> s.getId().equals(answer.path("slotId").asText())).findFirst().orElseThrow();
      return List.of(new Recommendation(selected, answer.path("confidence").asDouble(.75),
          answer.path("reasoning").asText("The recommended slot is available."),
          "We found a suitable replacement appointment with " + selected.getProvider().getName() + "."));
    } catch (Exception ex) {
      log.warn("AI scheduling failed; using rule-based fallback for appointment {}: {}", appointment.getId(), ex.getMessage());
      return fallback.recommend(appointment, reason, slots);
    }
  }
}