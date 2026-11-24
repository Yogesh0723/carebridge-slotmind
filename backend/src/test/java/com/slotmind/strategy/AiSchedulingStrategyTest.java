package com.slotmind.strategy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.slotmind.domain.enums.AppointmentType;
import com.slotmind.domain.enums.ProviderSpecialty;
import com.slotmind.domain.enums.ProviderStatus;
import com.slotmind.domain.enums.SlotStatus;
import com.slotmind.domain.enums.TriggerReason;
import com.slotmind.domain.model.Appointment;
import com.slotmind.domain.model.Provider;
import com.slotmind.domain.model.Slot;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiSchedulingStrategyTest {
  private HttpClient mockClient;
  private HttpResponse<String> mockResponse;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    mockClient = mock(HttpClient.class);
    mockResponse = mock(HttpResponse.class);
  }

  @Test
  void fallbackToRuleBasedWhenTokenMissing() {
    AiSchedulingStrategy strategy = new AiSchedulingStrategy(
        "https://ai.example.com", "", "gpt-4", "PC1", mockClient);

    Provider drChen = new Provider("PRV-1", "Dr. Chen", ProviderSpecialty.GENERAL);
    Slot originalSlot = new Slot("SLT-ORIG", drChen, LocalDateTime.now().plusDays(1), 30);
    Appointment appointment = new Appointment("APT-1", "John", "555-1234", AppointmentType.ROUTINE, drChen, originalSlot);

    Slot candidate = new Slot("SLT-1", drChen, LocalDateTime.now().plusHours(2), 30);

    List<Recommendation> recs = strategy.recommend(appointment, TriggerReason.PROVIDER_OFFLINE, List.of(candidate));
    assertNotNull(recs);
    assertFalse(recs.isEmpty());
    assertEquals("SLT-1", recs.get(0).slot().getId());
  }

  @Test
  void successfullyParseAiResponse() throws IOException, InterruptedException {
    AiSchedulingStrategy strategy = new AiSchedulingStrategy(
        "https://ai.example.com", "secret-token", "gpt-4", "PC1", mockClient);

    Provider drChen = new Provider("PRV-1", "Dr. Chen", ProviderSpecialty.GENERAL);
    Provider drSmith = new Provider("PRV-2", "Dr. Smith", ProviderSpecialty.GENERAL);
    Slot originalSlot = new Slot("SLT-ORIG", drChen, LocalDateTime.now().plusDays(1), 30);
    Appointment appointment = new Appointment("APT-1", "John", "555-1234", AppointmentType.URGENT, drChen, originalSlot);

    Slot candidate = new Slot("SLT-CAND", drSmith, LocalDateTime.now().plusHours(3), 30);

    String responseBody = """
        {
          "choices": [
            {
              "message": {
                "content": "{\\"slotId\\": \\"SLT-CAND\\", \\"alternateProviderId\\": \\"PRV-2\\", \\"confidence\\": 0.92, \\"reasoning\\": \\"Earliest matching general doctor\\", \\"patientScript\\": \\"We booked you with Dr. Smith.\\"}"
              }
            }
          ]
        }
        """;

    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn(responseBody);
    when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

    List<Recommendation> recs = strategy.recommend(appointment, TriggerReason.PROVIDER_OFFLINE, List.of(candidate));
    assertNotNull(recs);
    assertEquals(1, recs.size());
    assertEquals("SLT-CAND", recs.get(0).slot().getId());
    assertEquals(0.92, recs.get(0).confidence());
    assertEquals("PRV-2", recs.get(0).alternateProviderId());
    assertEquals("Earliest matching general doctor", recs.get(0).explanation());
  }

  @Test
  void parsesThreeDistinctAiSuggestionsInBestFirstOrder() throws IOException, InterruptedException {
    AiSchedulingStrategy strategy = new AiSchedulingStrategy(
        "https://ai.example.com", "secret-token", "gpt-4", "PC1", mockClient);

    Provider drChen = new Provider("PRV-1", "Dr. Chen", ProviderSpecialty.GENERAL);
    Provider drSmith = new Provider("PRV-2", "Dr. Smith", ProviderSpecialty.GENERAL);
    Slot originalSlot = new Slot("SLT-ORIG", drChen, LocalDateTime.now().plusDays(1), 30);
    Appointment appointment = new Appointment("APT-3", "Jordan", "555-1234", AppointmentType.ROUTINE, drChen, originalSlot);
    Slot first = new Slot("SLT-FIRST", drSmith, LocalDateTime.now().plusHours(2), 30);
    Slot second = new Slot("SLT-SECOND", drChen, LocalDateTime.now().plusHours(3), 30);
    Slot third = new Slot("SLT-THIRD", drSmith, LocalDateTime.now().plusHours(4), 30);

    String responseBody = """
        {"choices":[{"message":{"content":"[{\\"slotId\\":\\"SLT-FIRST\\",\\"confidence\\":0.95,\\"reasoning\\":\\"Best fit\\"},{\\"slotId\\":\\"SLT-SECOND\\",\\"confidence\\":0.85,\\"reasoning\\":\\"Continuity\\"},{\\"slotId\\":\\"SLT-FIRST\\",\\"confidence\\":0.80,\\"reasoning\\":\\"Duplicate\\"},{\\"slotId\\":\\"SLT-THIRD\\",\\"confidence\\":0.75,\\"reasoning\\":\\"Backup\\"}]"}}]}
        """;
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn(responseBody);
    when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

    List<Recommendation> recs = strategy.recommend(appointment, TriggerReason.SLOT_CANCELLED,
        List.of(first, second, third));

    assertEquals(List.of("SLT-FIRST", "SLT-SECOND", "SLT-THIRD"),
        recs.stream().map(r -> r.slot().getId()).toList());
    assertEquals(List.of(1, 2, 3), recs.stream().map(Recommendation::rank).toList());
    assertEquals(0.95, recs.get(0).confidence());
  }

  @Test
  void fallbackWhenAiReturnsInvalidSlotId() throws IOException, InterruptedException {
    AiSchedulingStrategy strategy = new AiSchedulingStrategy(
        "https://ai.example.com", "secret-token", "gpt-4", "PC1", mockClient);

    Provider drChen = new Provider("PRV-1", "Dr. Chen", ProviderSpecialty.GENERAL);
    Slot originalSlot = new Slot("SLT-ORIG", drChen, LocalDateTime.now().plusDays(1), 30);
    Appointment appointment = new Appointment("APT-1", "John", "555-1234", AppointmentType.ROUTINE, drChen, originalSlot);

    Slot candidate = new Slot("SLT-REAL", drChen, LocalDateTime.now().plusHours(2), 30);

    String responseBody = """
        {
          "choices": [
            {
              "message": {
                "content": "{\\"slotId\\": \\"SLT-HALLUCINATED\\", \\"confidence\\": 0.99, \\"reasoning\\": \\"test\\"}"
              }
            }
          ]
        }
        """;

    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn(responseBody);
    when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

    List<Recommendation> recs = strategy.recommend(appointment, TriggerReason.SLOT_CANCELLED, List.of(candidate));
    assertNotNull(recs);
    // Should safely fallback to rule-based and return the real candidate
    assertEquals("SLT-REAL", recs.get(0).slot().getId());
  }

  @Test
  void fallbackWhenAiReturnsNullSlotId() throws IOException, InterruptedException {
    AiSchedulingStrategy strategy = new AiSchedulingStrategy(
        "https://ai.example.com", "secret-token", "gpt-4", "PC1", mockClient);

    Provider drChen = new Provider("PRV-1", "Dr. Chen", ProviderSpecialty.GENERAL);
    Slot originalSlot = new Slot("SLT-ORIG", drChen, LocalDateTime.now().plusDays(1), 30);
    Appointment appointment = new Appointment("APT-NULL", "John", "555-1234", AppointmentType.ROUTINE,
        drChen, originalSlot);
    Slot candidate = new Slot("SLT-REAL", drChen, LocalDateTime.now().plusHours(2), 30);

    String responseBody = """
        {
          "choices": [
            {
              "message": {
                "content": "{\\"slotId\\": null, \\"confidence\\": 0.9}"
              }
            }
          ]
        }
        """;

    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn(responseBody);
    when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

    List<Recommendation> recs = strategy.recommend(appointment, TriggerReason.PROVIDER_OFFLINE, List.of(candidate));

    assertEquals("SLT-REAL", recs.get(0).slot().getId());
  }
}
