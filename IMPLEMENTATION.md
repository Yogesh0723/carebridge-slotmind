# SlotMind Technical Implementation Guide

This document explains the current SlotMind implementation from browser request to database write and response. It is based on the source under `frontend/src` and `backend/src/main/java` and complements `README.md` and `ADR.md`.

## 1. Architecture at a glance

```text
React/Vite frontend :5173
        |
        | HTTP JSON and Server-Sent Events
        v
Spring Boot REST API :8080
        |
        +--> Controllers
        +--> Application services
        +--> Domain entities and state transitions
        +--> Spring Data JPA repositories
        +--> SQLite: backend/slotmind.db
        |
        +--> after-commit disruption event
              |
              +--> async rescheduling agent
                    |
                    +--> rule-based or optional AI strategy
                    +--> persisted suggestions
```

The frontend is a presentation layer, not a correctness or authorization boundary. It filters slots for usability, but the backend repeats all booking and state checks.

## 2. Technology and configuration

### Backend

- Java 21 and Spring Boot 3.3.5.
- Spring Web MVC for REST and SSE.
- Spring Data JPA/Hibernate for persistence.
- SQLite through the Xerial JDBC driver.
- Spring transactions, optimistic locking, async task execution, and Actuator.

### Frontend

- React 18, TypeScript, and Vite.
- `fetch` for REST calls.
- `EventSource` for the reasoning stream.
- `lucide-react` for icons.

### Runtime settings

`backend/src/main/resources/application.yml` defines:

| Setting | Environment variable | Default |
|---|---|---|
| SQLite file | `SLOTMIND_DB` | `slotmind.db` |
| Strategy | `SLOTMIND_STRATEGY` | `RULE_BASED` |
| AI URL | `SLOTMIND_AI_URL` | configured gateway URL |
| AI token | `SLOTMIND_AI_TOKEN` | empty |
| AI model | `SLOTMIND_AI_MODEL` | configured model |
| AI product header | `SLOTMIND_AI_PRODUCT` | `PC1` |
| API port | current YAML | `8080` |

The frontend uses `http://localhost:8080/api`. CORS allows `http://localhost:5173`.

## 3. Package responsibilities

```text
com.slotmind
├── controller       REST and SSE adapters
├── dto              Request and response records
├── service          Application use cases and transactions
├── domain.model     JPA entities and legal state transitions
├── domain.enums     Status and type enums
├── domain.event     Application event records
├── repository       Spring Data JPA interfaces
├── agent            Async after-commit rescheduling listener
├── strategy         Rule, AI, and room-constraint policies
├── config           Executor, CORS, and seed data
└── exception        REST exception mapping
```

Controllers deserialize input, invoke services, and map entities to DTOs. Services enforce use-case rules and transaction boundaries. Entities enforce state transitions.

## 4. Persistence model and state machines

### Provider

`Provider` contains an ID, name, specialty (`GENERAL`, `SPECIALIST`, `PEDIATRIC`), status (`AVAILABLE`, `BUSY`, `OFFLINE`), daily limit, and a JPA `@Version` value.

### Slot

`Slot` contains its provider, start time, duration, room, equipment, status, and version.

```text
OPEN --book()--> BOOKED
BOOKED --release()--> OPEN
OPEN/BOOKED --cancel()--> CANCELLED
```

`book()` rejects a non-open slot. `release()` only changes a booked slot back to open.

### Appointment

```text
CONFIRMED --markPending()--> RESCHEDULE_PENDING
RESCHEDULE_PENDING --reschedule(newSlot)--> RESCHEDULED
CONFIRMED or pending --cancel()--> CANCELLED
RESCHEDULE_PENDING --restoreConfirmed()--> CONFIRMED
```

`COMPLETED` exists in `AppointmentStatus`, although the current API does not expose a completion endpoint.

### Reschedule suggestion

Suggestions contain an appointment, recommended slot, optional alternate provider, trigger reason, confidence, explanation, patient script, status, and version. A database unique constraint prevents duplicate `(appointment_id, slot_id)` pairs.

## 5. Common request path

```text
Browser event
  -> frontend api/client.ts
  -> fetch(http://localhost:8080/api/...)
  -> Spring controller
  -> application service
  -> repository/entity operations
  -> transaction commit
  -> DTO JSON response
  -> React state update
```

The frontend `request<T>()` helper throws an `Error` containing the response body for any non-2xx response. React action handlers catch it and display the error banner.

## 6. Provider flows

### List providers

```http
GET /api/providers
```

`ProviderController.getAllProviders()` calls the `ProviderService` interface, whose `ProviderServiceImpl.findAll()` calls `ProviderRepository.findAll()`. The JSON list is stored in React state and used for the care-team list and provider selector.

### Create provider

```http
POST /api/providers
Content-Type: application/json

{"name":"Dr. Example","specialty":"GENERAL"}
```

Spring maps the body to `ProviderRequest`. `ProviderService.create()` creates an ID, constructs a provider whose default status is `AVAILABLE`, saves it, and returns the entity. The current method has no explicit service-level `@Transactional` annotation.

### Change provider status

```http
PATCH /api/providers/{id}/status?value=OFFLINE
```

`ProviderServiceImpl.updateStatus()` is transactional:

1. Load the provider.
2. Change and save its status.
3. If the new status is `OFFLINE`, find its confirmed appointments.
4. Mark each appointment `RESCHEDULE_PENDING` and save it.
5. Publish `DisruptionEvent(appointmentId, PROVIDER_OFFLINE)` for each appointment.
6. Commit.
7. Return the provider immediately.

If the new status is `AVAILABLE`, pending appointments for that provider are restored to `CONFIRMED` and their pending suggestions are rejected in the same transaction. No new disruption event is published on recovery.

## 7. Slot flows

### List slots

```http
GET /api/slots
GET /api/slots/available
```

`GET /api/slots` loads all slots and maps them to `SlotResponse`, including provider ID/name/status. The available endpoint uses `findByStatusAndProviderStatus(OPEN, AVAILABLE)`.

### Create a slot

```http
POST /api/slots
```

`SlotService.create()` loads the provider, creates a slot with default status `OPEN`, copies room/equipment, saves it, and returns its DTO. A missing provider currently uses a bare `orElseThrow()`, which is discussed in the exception section.

### Cancel a slot

```http
DELETE /api/slots/{id}
PATCH /api/slots/{id}/status?value=CANCELLED
```

`SlotService.cancel()` is transactional:

1. Load and cancel the slot.
2. Save the cancelled slot.
3. Find its confirmed appointment, if any.
4. Mark that appointment `RESCHEDULE_PENDING` and save it.
5. Publish `DisruptionEvent(appointmentId, SLOT_CANCELLED)`.
6. Commit.
7. The after-commit agent later creates suggestions.

The DELETE endpoint returns no body. The PATCH endpoint returns the updated slot DTO.

## 8. Appointment flows

### List appointments

```http
GET /api/appointments
GET /api/appointments?status=RESCHEDULE_PENDING
```

The controller accepts an optional enum status, loads appointments, optionally filters them, and maps them to `AppointmentResponse`.

### Create an appointment

```http
POST /api/appointments
```

`AppointmentService.create()` is transactional. The validation order is:

1. Patient name, contact, type, provider ID, and slot ID must be present.
2. Provider must exist and be `AVAILABLE`.
3. Slot must exist, belong to the requested provider, be `OPEN`, and have an available provider.
4. Required duration must fit the slot: consultation is 45 minutes, procedure is 60, other supported types are 30.
5. `MORNING` rejects a slot at or after noon; `AFTERNOON` rejects a slot before noon.
6. The provider’s count for the slot’s day must be below `maxDailyAppointments`.

When valid, `slot.book()` changes the slot to `BOOKED`, a confirmed appointment is created, optional requirements/preferences are copied, and the appointment is saved. Since this is one transaction, a later failure rolls back both the slot and appointment changes.

### Cancel an appointment

```http
POST /api/appointments/{id}/cancel
```

The transactional service loads the appointment, rejects a second cancellation, changes it to `CANCELLED`, releases its booked slot, saves both, and commits. Patient cancellation does not publish a rescheduling event.

### Disrupt an appointment

```http
POST /api/appointments/{id}/disrupt?reason=PROVIDER_OFFLINE
POST /api/appointments/{id}/suggest
```

`disrupt()` marks a confirmed appointment pending, saves it, publishes the event, and returns the appointment. The convenience `/suggest` endpoint uses `TriggerReason.INITIAL`. Suggestion generation is not complete when this HTTP response returns; the frontend observes it through polling.

## 9. Transactional event listener and async agent

The listener is declared as:

```java
@Async("taskExecutor")
@Transactional(propagation = REQUIRES_NEW)
@TransactionalEventListener(phase = AFTER_COMMIT)
```

### Why this design is used

The disruption request must not wait for an external AI gateway or suggestion persistence. The event is only processed after the state-changing transaction commits. `@Async("taskExecutor")` moves work to a bounded virtual-thread executor. `REQUIRES_NEW` gives the agent its own transaction.

The executor permits up to 32 concurrent tasks, uses Java 21 virtual threads, and waits up to 30 seconds for tasks during application shutdown. Spring graceful server shutdown and a 30-second lifecycle shutdown phase allow in-flight requests and agent work to complete cleanly. SQLite remains configured with a single JDBC connection because SQLite write concurrency is serialized; virtual threads improve waiting-task scalability without creating database lock contention.

### Listener algorithm

`RescheduleAgentListener.handle()`:

1. Loads the appointment from the event ID.
2. Skips it if it is no longer `RESCHEDULE_PENDING`.
3. Skips it if its original provider is available again.
4. Loads open slots whose providers are available.
5. Calls the selected `SchedulingStrategy` with appointment, trigger reason, and candidates.
6. Calls `SuggestionService.create()` for each recommendation.
7. Rechecks pending appointment state and open/available candidate state.
8. Validates an AI alternate provider ID through `ProviderRepository`.
9. Saves suggestions.

The listener catches and logs all exceptions. A background failure therefore does not change the already-committed appointment state or produce an HTTP error. At present, logs are the only failure record for this background job.

## 10. Strategy selection and recommendation

The strategy contract is:

```java
List<Recommendation> recommend(
    Appointment appointment,
    TriggerReason reason,
    List<Slot> slots);
```

`Recommendation` includes the slot, confidence, explanation, patient script, and optional alternate provider ID.

`StrategyRegistry` selects the strategy at startup:

| `SLOTMIND_STRATEGY` | Implementation |
|---|---|
| `RULE_BASED` or unknown | `RuleBasedSchedulingStrategy` |
| `AI` | `AiSchedulingStrategy` |
| `ROOM_CONSTRAINT` | `RoomConstraintStrategy` |

### Rule-based strategy

The rule strategy:

1. Keeps only matching-specialty slots.
2. Sorts same-provider slots first.
3. Sorts other matching-specialty providers after them.
4. Sorts by start time within those groups.
5. Limits results to three.
6. Produces confidence `.82`, an explanation, and patient script.

### AI strategy

AI runs only when a token exists and candidates are non-empty. It builds different prompt context for `PROVIDER_OFFLINE`, `SLOT_CANCELLED`, and `INITIAL`/`MANUAL`. It requests:

```json
{"slotId":"...","alternateProviderId":"...","confidence":0.88,"reasoning":"..."}
```

The returned slot ID must match an actual candidate. Gateway errors, malformed output, missing slots, or network errors log a warning and fall back to the rule strategy. AI is advisory and cannot directly write arbitrary slot/provider IDs.

## 11. Suggestion endpoints

### List suggestions

```http
GET /api/appointments/{appointmentId}/suggestions
```

The service loads suggestions and hides pending suggestions whose slots are no longer open or whose providers are no longer available. The DTO includes trigger reason, confidence, explanation, patient script, start time, provider name, and alternate provider ID.

### Accept a suggestion

```http
POST /api/suggestions/{suggestionId}/accept
```

This transaction is atomic:

```text
begin
  load suggestion
  require suggestion PENDING
  require appointment RESCHEDULE_PENDING
  require replacement slot OPEN
  replacementSlot.book()
  appointment.reschedule(replacementSlot)
  oldSlot.release()
  save old slot
  suggestion.accept()
  reject other pending suggestions for appointment
  save accepted suggestion
commit
```

`Appointment.reschedule()` also changes the appointment’s provider to the replacement slot provider. JPA `@Version` fields allow concurrent updates to fail instead of silently overwriting another user’s change. `IllegalStateException` becomes HTTP 409.

### Reject a suggestion

```http
POST /api/suggestions/{suggestionId}/reject
```

The transactional service requires the suggestion to be pending, changes it to rejected, saves it, and returns its DTO.

## 12. SSE reasoning endpoint

```http
GET /api/appointments/{id}/suggest/stream
Accept: text/event-stream
```

The controller creates a 120-second `SseEmitter` and sends:

1. A `reasoning` event with the progress text.
2. A `complete` event containing the current suggestion DTO list.
3. Emitter completion.

The frontend opens `EventSource`, renders the progress text, handles `reasoning`, replaces it on `complete`, and closes the source. This is a concise progress/final snapshot stream; it does not stream each internal strategy step.

## 13. Exception behavior

`ApiErrorHandler` is a `@RestControllerAdvice`:

| Exception | Status | Response |
|---|---:|---|
| `IllegalArgumentException` | 400 | `{ "error": "..." }` |
| `IllegalStateException` | 409 | `{ "error": "..." }` |

Null or blank exception messages use safe fallback text.

Examples:

- Missing required booking fields: `IllegalArgumentException` → 400.
- Missing provider in booking: `IllegalArgumentException` → 400.
- Offline provider or unavailable slot: `IllegalStateException` → 409.
- Provider/slot mismatch: `IllegalStateException` → 409.
- Slot too short: `IllegalStateException` → 409.
- Already-cancelled appointment: `IllegalStateException` → 409.
- Already-resolved suggestion: `IllegalStateException` → 409.

### Current generic-exception edge case

Some methods use bare `repository.findById(...).orElseThrow()`. A missing record in those paths produces `NoSuchElementException`, which is not explicitly handled by `ApiErrorHandler`; Spring may therefore return a generic 500 response instead of the normal `{error: ...}` format. This is particularly relevant to some missing slot, appointment, provider, or suggestion requests.

Async listener exceptions are caught and logged rather than returned to a caller.

## 14. Frontend behavior

### Initial load and polling

On mount, `main.tsx` loads appointments, providers, and slots in parallel. It then requests suggestions for every `RESCHEDULE_PENDING` appointment and keeps pending suggestions for display.

The UI starts `setInterval(load, 4000)` and clears it on unmount. This four-second poll is necessary because disruption processing is after-commit and asynchronous.

### Mutating actions

The `action()` helper prevents duplicate actions, sets a busy key, clears old errors, calls the API, shows a success notice, reloads all data, catches errors, and clears busy state in `finally`.

### Booking UI

The frontend shows only open slots with available providers and optionally filters by selected provider. The backend still performs authoritative provider, ownership, duration, preference, capacity, and current-state validation.

### Suggestions

Cards show trigger reason, provider, explanation, confidence, patient script, copy action, live reasoning action, and accept/reject actions. Every mutation reloads the dashboard after completion.

## 15. Complete disruption sequence

```text
Receptionist -> React: click provider Offline
React -> REST: PATCH /api/providers/{id}/status?value=OFFLINE
REST -> ProviderService: status()
ProviderService -> SQLite: provider OFFLINE; appointments PENDING
ProviderService -> Spring: publish DisruptionEvent
Spring -> SQLite: commit original transaction
REST --> React: provider response immediately
Spring -> Async executor: AFTER_COMMIT listener
Agent -> SQLite: query OPEN slots for AVAILABLE providers
Agent -> Strategy: recommend appointment, reason, candidates
Strategy --> Agent: up to three recommendations
Agent -> SQLite: persist suggestions in new transaction
React -> REST: poll every four seconds
REST --> React: pending appointment and suggestions
React --> Receptionist: render suggestion cards
```

The initial status response does not guarantee that suggestions already exist. They become visible after the background transaction completes and the next poll runs.

## 16. Startup and seed data

`SeedDataLoader` runs on startup. If providers, slots, and appointments already exist, it does nothing. Otherwise it creates the demo roster, slots, and appointments. The SQLite file is persistent; restarting does not reset state. Delete the configured database file only when a clean demo dataset is required.

## 17. Transaction and concurrency summary

| Use case | Transaction | Async work |
|---|---|---|
| Create appointment | slot booking and appointment save are atomic | none |
| Cancel appointment | appointment cancel and slot release are atomic | none |
| Provider offline | provider and affected appointments are atomic | suggestions after commit |
| Provider recovery | restore and pending-suggestion rejection are atomic | none |
| Cancel slot | slot cancel and appointment pending state are atomic | suggestions after commit |
| Accept suggestion | replacement booking, reschedule, release, and rejection are atomic | none |
| Reject suggestion | status change is transactional | none |

SQLite is configured with one Hikari connection. This is predictable for a local demo but serializes writes and is not the preferred production multi-user database.

## 18. Security and operations

- Keep AI tokens in environment variables; never commit them.
- Never place credentials in source, command arguments, or Git remote URLs.
- Backend validation is authoritative; frontend filtering is not authorization.
- CORS currently allows only `http://localhost:5173`.
- Actuator exposes health and info.
- Debug logs are enabled for `com.slotmind` in the current YAML.
- Review production logs for patient-data exposure.

## 19. Validation commands

```powershell
cd C:\Users\zycus\Desktop\Hackthon_Yogesh\backend
mvn clean test

cd ..\frontend
npm run build
```

The tests cover slot and appointment state transitions, specialty matching, same-provider preference, earliest-three ordering, duration and availability checks, cancellation release, and conflict behavior.

## 20. Demo checklist

1. Start backend and frontend.
2. Confirm providers, slots, and appointments load.
3. Set a provider with confirmed appointments to `OFFLINE`.
4. Observe appointments become `RESCHEDULE_PENDING`.
5. Wait for the async listener and four-second poll.
6. Confirm suggestions show reason, explanation, confidence, and patient script.
7. Accept one suggestion and verify replacement booking, reschedule, old-slot release, and competing-suggestion rejection.
8. In another scenario, restore the original provider and verify pending appointments return to confirmed.
9. Cancel an appointment and verify its slot is released.
10. Open live reasoning and verify the SSE progress and completion events.