# SlotMind

SlotMind is a Spring Boot + SQLite scheduling workspace. It handles provider availability, appointment booking, cancellations, disruption events, and rule/AI-based rescheduling suggestions.

## Start

Backend (Java 21 and Maven):

```powershell
cd C:\Users\zycus\Desktop\Hackthon_Yogesh\backend
mvn clean test
java -jar target\slotmind-backend-1.0.0.jar
```

Frontend (Node.js):

```powershell
cd C:\Users\zycus\Desktop\Hackthon_Yogesh\frontend
npm install
npm run dev
```

Open `http://localhost:5173/`. The API is `http://localhost:8080/api`.

## Data and the SQLite file

`SeedDataLoader.java` is startup demo data, not the database. It inserts the roster, slots, and appointments only when the database is empty. The persistent database is `backend/slotmind.db`, configured by `SLOTMIND_DB` (default: `slotmind.db`). Once data exists, restarting the application does not reset it. Delete the database only when a clean demo dataset is required.

Every appointment displays both provider ID and provider name, for example `PRV-001 · Dr. Sarah Chen`.

## Scheduling rules

- A new booking requires an `AVAILABLE` provider and an `OPEN` slot owned by that provider.
- A slot must meet the appointment duration and patient time preference.
- A recommendation requires the same specialty as the original provider, so a GENERAL appointment does not suggest `PRV-002 · Dr. James Okonkwo` (SPECIALIST).
- Busy, offline, booked, and cancelled slots are not candidates.
- Provider-offline and slot-cancelled events mark confirmed appointments `RESCHEDULE_PENDING`; the async agent creates up to three earliest matching suggestions.
- Accept is transactional: the replacement slot is booked, the appointment is rescheduled, the old slot is released, and competing suggestions are rejected.
- Patient cancellation marks the appointment cancelled and releases its booked slot.
- If the original provider becomes available again before a suggestion is accepted, the appointment is restored to confirmed and pending suggestions are rejected.
- Optimistic locking and HTTP `409 Conflict` responses protect concurrent updates.

## UI behavior

The dashboard includes Overview, Care team, and Availability views, provider IDs/names, status and availability badges, loading skeletons, polling status, action spinners/locks, toasts, sticky glass header, scrollable schedule panels, hover states, semantic appointment/trigger badges, confidence/load bars, and a patient-script copy action. The reasoning stream endpoint is available at `GET /api/appointments/{id}/suggest/stream`; the UI renders its reasoning and final suggestions when opened.

## Tests

```powershell
cd backend
mvn clean test
```

Tests cover slot state transitions, disruption/reschedule lifecycle, specialty matching, exclusion of wrong specialties, earliest-three ordering, duration validation, availability checks, cancellation release, and API conflict behavior.

Debug logs are controlled in `backend/src/main/resources/application.yml`:

```yaml
logging:
  level:
    com.slotmind: DEBUG
```