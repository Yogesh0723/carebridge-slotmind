# SlotMind — Architecture Decision Records (ADR)

> **Project:** SlotMind — AI-Assisted Clinic Appointment Re-Scheduling Engine  
> **Status:** Accepted  
> **Date:** 2026-11-24  
> **Standard Format:** Context → Options → Decision → Tradeoffs  
> **Brief Reference:** `slotmind-brief 1 (1).html` (Tasks T-1 through T-6, AGT-1 to AGT-3)

---

## Executive Architecture Summary

SlotMind is a reactive clinical scheduling engine designed for CareBridge clinics. When a care provider calls in sick (`OFFLINE`) or a room/time slot is cancelled (`CANCELLED`), the engine automatically detects disrupted appointments, evaluates candidate slots using an AI/rules scheduling strategy, and surfaces ranked suggestions for front-desk staff approval.

```
┌────────────────────────────────────────────────────────┐
│               Front-Desk UI (React 18 SPA)             │
│   Live Polling · SSE Streaming · Script Copy · Badges  │
└───────────────────────────┬────────────────────────────┘
                            │ HTTP REST / SSE (:8080)
                            ▼
┌────────────────────────────────────────────────────────┐
│              Spring Boot Application Layer             │
│   Controllers ──► Services ──► Domain State Machine   │
│                        │                               │
│              ApplicationEventPublisher                 │
│                        │ DisruptionEvent (AFTER_COMMIT)│
│                        ▼                               │
│              RescheduleAgentListener                   │
│                        │ (@Async, REQUIRES_NEW)        │
│                        ▼                               │
│               SchedulingStrategy                       │
│    (AI / Rule-Based / Room-Constraint Strategy)        │
└───────────────────────────┬────────────────────────────┘
                            │ JPA / Hibernate
                            ▼
┌────────────────────────────────────────────────────────┐
│                 SQLite Database                        │
│   Atomic Transactions · Optimistic Locking (@Version)  │
└────────────────────────────────────────────────────────┘
```

---

## ADR-1: Placement of Scheduling and Booking Logic

### Context
When booking an appointment or changing slot states, the system must protect clinic invariants: slots must be OPEN, provider must be AVAILABLE, durations must match appointment types (e.g. 60m for PROCEDURE, 45m for CONSULTATION, 30m for ROUTINE/URGENT), patient time preferences (MORNING/AFTERNOON) must be respected, and doctor daily limits cannot be exceeded.

Where should validation and state transitions live: in an anemic data model orchestrated entirely by services, or inside a rich domain model with defensive domain boundaries?

### Options Considered
- **Option 1: Service-heavy (Anemic Domain Model)**  
  Entities are plain property holders (getters/setters). `AppointmentService` directly sets status fields on `Slot` and `Appointment`.
- **Option 2: Pure Domain Model (DDD Aggregate)**  
  All entities and collections encapsulated under a single root aggregate (e.g., `ScheduleAggregate`), with no direct Spring repositories exposed.
- **Option 3: Hybrid Domain-Driven Design (Selected)**  
  Entities own their internal lifecycle state transitions (`slot.book()`, `slot.release()`, `slot.cancel()`, `appointment.markPending()`, `appointment.reschedule()`), throwing domain exceptions on illegal state transitions. Services orchestrate cross-entity queries, transaction boundaries, capacity calculations, and event publishing.

### Decision
We adopted **Option 3: Hybrid Domain-Driven Design**.
1. **Domain Boundary Invariants**: Entities strictly enforce legal state transitions:
   - `Slot.book()` throws `IllegalStateException` if `status != OPEN`.
   - `Slot.release()` only transitions from `BOOKED` back to `OPEN`.
   - `Appointment.markPending()` requires `CONFIRMED` status.
   - `Appointment.reschedule(newSlot)` requires `RESCHEDULE_PENDING` status.
2. **Service Orchestration**: `AppointmentServiceImpl` manages cross-entity rules (matching doctor ID between slot and provider, daily appointment count vs `provider.getMaxDailyAppointments()`, duration and time-of-day checks).

### Tradeoffs
- **Pros:** Prevents invalid entity states anywhere in the system; domain rules are unit-testable without database mocks; clean separation between data consistency and infrastructure.
- **Cons:** Slight distributed responsibility: entity handles local state validity while service handles multi-entity validation (e.g., daily capacity).

---

## ADR-2: Runtime Strategy Switching

### Context
The system requires pluggable scheduling strategies:
- Deterministic **Rule-Based** matching (same doctor preferred, same specialty, sorted by earliest time).
- **AI-Powered** scheduling with LLM gateway integration.
- Sprint 2 **Room/Equipment Constraint** strategy.

The scheduling strategy must be switchable at runtime through configuration without modifying business logic, and must serve both HTTP on-demand re-scheduling requests and asynchronous agentic loops identically.

### Options Considered
- **Option 1: Static `if/else` branching in Service**  
  Hardcoded checks inside `RescheduleAgentListener` and `SuggestionService`.
- **Option 2: Strategy Pattern with Spring `@ConditionalOnProperty` Beans**  
  Instantiating only one bean based on application properties.
- **Option 3: Strategy Registry Bean Pattern (Selected)**  
  A centralized `StrategyRegistry` configuration bean that reads `slotmind.strategy` (`RULE_BASED`, `AI`, `ROOM_CONSTRAINT`), wires credentials safely from environment variables, and exposes a uniform `SchedulingStrategy` interface bean to both caller paths.

### Decision
We adopted **Option 3: Strategy Registry Pattern**.
- The `SchedulingStrategy` contract defines:
  ```java
  List<Recommendation> recommend(Appointment appointment, TriggerReason reason, List<Slot> slots);
  ```
- `StrategyRegistry` resolves the active bean on startup:
  - `"AI"`: `AiSchedulingStrategy` (with automatic fallback to `RuleBasedSchedulingStrategy` if token is absent or API fails).
  - `"ROOM_CONSTRAINT"`: `RoomConstraintStrategy` (verifies room, equipment, and duration).
  - Default: `RuleBasedSchedulingStrategy`.
- Both `RescheduleAgentListener` (async) and on-demand endpoints call the identical contract.

### Tradeoffs
- **Pros:** Completely decoupled; adding new strategies (e.g., patient waitlist routing or downstream queue pressure) requires zero changes to the agentic loop; fail-safe fallback built into AI strategy.
- **Cons:** Strategy is selected at application context initialization; switching mid-run without restart requires dynamic bean proxying (deferred to Sprint 3).

---

## ADR-3: Transactional Accept & Concurrency Control (AGT-3)

### Context
When front-desk staff accept a reschedule suggestion, the operation must update four distinct records atomically:
1. Book the new slot.
2. Update the appointment with the new slot/provider and transition status to `RESCHEDULED`.
3. Release the old slot back to `OPEN`.
4. Transition the accepted suggestion to `ACCEPTED`, while auto-rejecting competing `PENDING` suggestions.

**Race Condition Risk (AGT-3):** Two coordinators working the same cancellation queue simultaneously could accept two different suggestions pointing to the exact same open slot, causing a double-booking disaster.

### Options Considered
- **Option 1: Database-level Unique Constraints Only**  
  Rely on SQL unique indexes. Does not handle state transition invariants cleanly across multiple rows.
- **Option 2: Pessimistic Database Row Locking (`PESSIMISTIC_WRITE`)**  
  Locks slot rows during read. SQLite serializes writes, so heavy pessimistic locking leads to lock timeouts and thread starvation.
- **Option 3: Atomic `@Transactional` with JPA Optimistic Locking (`@Version`) (Selected)**  
  All mutations execute in a single Spring transaction (`SuggestionServiceImpl.accept()`). Every entity (`Appointment`, `Slot`, `Provider`, `RescheduleSuggestion`) carries a `@Version private long version` column.

### Decision
We adopted **Option 3: Atomic `@Transactional` + Optimistic Locking**.
- **Execution Flow in `SuggestionServiceImpl.accept(id)`**:
  1. Guard checks: Suggestion must be `PENDING`, appointment must be `RESCHEDULE_PENDING`, replacement slot must be `OPEN`.
  2. `newSlot.book()` marks target slot `BOOKED`.
  3. `appointment.reschedule(newSlot)` updates references and sets status to `RESCHEDULED`.
  4. `oldSlot.release()` returns the previous slot to `OPEN`.
  5. Other pending suggestions for this appointment are marked `REJECTED`.
  6. The accepted suggestion is saved as `ACCEPTED`.
- **Double-Booking Prevention (AGT-3)**:
  If Coordinator B attempts to accept another suggestion for the same slot, Hibernate detects the incremented `@Version` on `newSlot` and throws an `OptimisticLockException` (or `IllegalStateException` on `newSlot.book()`). The API error handler translates this into HTTP `409 Conflict`, completely preventing double-booking without blocking the database.

### Tradeoffs
- **Pros:** 100% ACID compliance; zero possibility of phantom double-bookings; non-blocking performance; clean conflict feedback to staff.
- **Cons:** The conflicting second user receives an error and must refresh their view to see updated slot availability.

---

## ADR-4: Agentic Loop Decoupling (AGT-1)

### Context
Disruptions occur under different operational circumstances:
- **Provider Offline**: 1 event affects **many** appointments (the doctor's entire day).
- **Slot Cancelled**: 1 event affects **exactly 1** appointment.
- **Manual/Initial Disruption**: Triggered on-demand by staff for a single patient.

The API must return HTTP `200/202` immediately to keep front-desk UI responsive. The AI/rules agent must run in the background without blocking the request thread, and must not create suggestions if the triggering transaction fails or rolls back.

### Options Considered
- **Option 1: Synchronous In-line Processing in Controller**  
  Calling the AI/rule engine directly within the HTTP request. Freezes UI for several seconds while LLM generates recommendations.
- **Option 2: Message Broker (RabbitMQ / Kafka)**  
  Decoupled external queue. Heavy infrastructure overhead inappropriate for local solo clinic deployment.
- **Option 3: Spring ApplicationEventPublisher with `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` (Selected)**  
  In-process event-driven architecture using Spring's event bus.

### Decision
We adopted **Option 3: Asynchronous After-Commit Event Decoupling**.
- **Two Triggers, One Agent Loop (AGT-1)**:
  - Both `ProviderService.status(OFFLINE)` and `SlotService.cancel()` publish lightweight immutable `DisruptionEvent(appointmentId, reason)` records.
  - For `PROVIDER_OFFLINE`, the service iterates all confirmed appointments and emits one event per appointment.
  - For `SLOT_CANCELLED`, the service identifies the affected appointment and emits a single event.
- **Transactional Decoupling**:
  `RescheduleAgentListener.handle(DisruptionEvent)` is annotated with:
  ```java
  @Async("taskExecutor")
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  ```
  - **`AFTER_COMMIT`**: The listener only triggers after the primary transaction commits. If marking a doctor offline fails, no phantom suggestions are generated.
  - **`@Async("taskExecutor")`**: Runs on a dedicated thread pool (`slotmind-agent-1..8`), returning immediately to the caller.
  - **`REQUIRES_NEW`**: The agent operates in its own isolated transaction, preventing background AI or query failures from affecting the primary entity commit.
- **Recovery & Stale Event Protection**:
  Before calling the scheduling strategy, the listener checks:
  1. Is the appointment still `RESCHEDULE_PENDING`?
  2. If the trigger was `PROVIDER_OFFLINE`, is the original provider still offline? If the doctor recovered to `AVAILABLE` in the interim, the agent silently aborts.

### Tradeoffs
- **Pros:** Instant API response times; guaranteed transactional consistency; zero risk of orphan suggestions from rolled-back transactions; scalable thread pool.
- **Cons:** Eventual consistency: front-desk dashboard requires polling (implemented every 4 seconds) or SSE to render newly generated suggestions.

---

## ADR-5: Extensibility, AI Safety, and Exclusions (AGT-2)

### Context
The hackathon brief outlines Sprint 1 core functionality, while defining roadmap extensions for Sprint 2 (Room/Equipment Constraints, Patient Preferences) and Sprint 3 (Waitlists, SMS/Email Notifications, Patient Portal). The architecture must welcome these additions without rewrites.

Furthermore, AI recommendations must be clinically safe, verified against live data, and patient-friendly (AGT-2).

### Options Considered
- **Option 1: Let the LLM autonomously decide booking changes**  
  Direct agentic booking without human-in-the-loop. High clinical risk; prone to hallucinations and patient dissatisfaction.
- **Option 2: Human-in-the-Loop with Patient-Centric AI Outputs (Selected)**  
  AI acts strictly as an advisory system generating recommendations. Front-desk staff maintain complete authority to Accept or Reject after speaking with the patient.

### Decision
We adopted **Option 2: Human-in-the-Loop with Guardrailed AI Guidance**.
1. **Patient-Friendly AI Output (AGT-2)**:
   - System instructions explicitly instruct the model to produce both technical `reasoning` and an empathetic, conversational `patientScript` (e.g. *"Hello John, Dr. Chen had an emergency today. We reserved an appointment with Dr. Sharma at 2:30 PM..."*).
   - Staff can copy this script with one click from the UI.
2. **AI Guardrails & Verification**:
   - The AI output JSON schema is strictly verified: `slotId`, `alternateProviderId`, `confidence`, `reasoning`, `patientScript`.
   - `AiSchedulingStrategy` verifies that the recommended `slotId` exists in the candidates list and is currently `OPEN`. If invalid, it silently falls back to `RuleBasedSchedulingStrategy`.
3. **Sprint 2 Extensibility**:
   - `RoomConstraintStrategy` is already implemented and registered under `StrategyRegistry`.
   - Entity models already persist `roomRequirement`, `equipmentRequirement`, and `patientPreference`.
4. **Deliberate Exclusions (Deferred to Sprint 2 & 3)**:
   - Patient self-service portal: Out of scope for Sprint 1.
   - Outbound SMS/Twilio notifications: Mocked in script text, actual gateway deferred to Sprint 3.
   - Waitlist auto-filling: Deferred to Sprint 3.

### Tradeoffs
- **Pros:** Zero risk of automated mis-scheduling; staff retain complete control; smooth runway into Sprint 2 and Sprint 3 features.
- **Cons:** Requires human interaction to finalize the rescheduling process.

---

## Architecture Quality & Compliance Matrix

| Criterion | Architectural Guarantee | Verification |
|---|---|---|
| **No Double Booking** | `@Version` optimistic locking on `Slot` + `@Transactional` in `SuggestionService.accept()` | `SlotAppointmentStateTest`, concurrency integration tests |
| **Pluggable Engine** | `SchedulingStrategy` interface resolved via `StrategyRegistry` | `AiSchedulingStrategyTest`, `RuleBasedSchedulingStrategyTest` |
| **Agentic Decoupling** | `@Async` + `@TransactionalEventListener(phase = AFTER_COMMIT)` | `RescheduleAgentListenerTest` |
| **Fail-Safe AI** | Strict candidate validation + automatic rule-based fallback | `AiSchedulingStrategyTest.fallbackWhenAiReturnsInvalidSlotId()` |
| **Error Handling** | `@RestControllerAdvice` maps exceptions to HTTP 400, 404, 409 | `ApiErrorHandlerTest` |
| **Sprint 1 Walkthrough** | Book → `PATCH Provider OFFLINE` → Auto Suggestions → Accept → Atomic Rebook | E2E verified via live app and test suite |
