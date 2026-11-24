# SlotMind Architecture Decision Record

## Context

Appointment disruptions need fast, explainable alternatives without blocking the scheduling API. The system must run locally with a single-file SQLite database and optionally use an enterprise LLM gateway.

## Options

We considered synchronous LLM calls, a rules-only engine, and an asynchronous strategy abstraction. We also considered PostgreSQL, but local deployment requirements favor SQLite.

## Decision

SlotMind uses Spring Boot, JPA, SQLite, optimistic locking, and an asynchronous after-commit disruption listener. `SchedulingStrategy` allows runtime replacement; the current default is deterministic specialty/time matching. AI credentials and endpoint are environment-backed (`SLOTMIND_AI_URL`, `SLOTMIND_AI_TOKEN`, `SLOTMIND_AI_MODEL`) and are never committed.

## Tradeoffs

SQLite is simple and portable but serializes writes, so Hikari is limited to one connection. Async processing makes the user workflow responsive, but suggestions are eventually consistent. Optimistic locking prevents double acceptance; rejected or already-resolved suggestions fail instead of silently overwriting state.

## Status and scope

- **Status:** Accepted
- **Date:** 2026-11-24
- **Scope:** Backend API, scheduling domain, persistence, asynchronous rescheduling, and operations dashboard.

## Context

SlotMind is a clinic scheduling workspace that must keep appointments bookable and explainable when a provider becomes unavailable or a slot is cancelled. Reception staff need immediate feedback while the system protects against double booking, invalid providers, invalid slots, specialty mismatches, and concurrent suggestion acceptance.

## Decisions

### Application architecture

The application uses a React/Vite/TypeScript frontend and a Java 21 Spring Boot REST API. The backend uses Spring Data JPA with SQLite for portable local persistence. Domain entities explicitly represent providers, slots, appointments, and reschedule suggestions. The application root package is `com.slotmind`, so Spring discovers controllers, services, configuration, and the dedicated `com.slotmind.exception.ApiErrorHandler` automatically.

### Persistence

SQLite is the default because local deployment requires no database server. Its connection pool is limited to one connection for reliable local operation. PostgreSQL was considered but rejected as the default because it adds infrastructure; JPA repositories leave a future migration possible.

`SeedDataLoader` inserts demo data only when the database is empty. `backend/slotmind.db` stores ongoing state and is not committed.

### Scheduling and correctness

New booking validation is performed inside the backend transaction, not trusted from frontend filtering. A booking requires:

- an `AVAILABLE` provider;
- an `OPEN` slot owned by that provider;
- sufficient duration for the appointment type;
- a matching patient time preference; and
- capacity below `Provider.getMaxDailyAppointments()`.

The daily-limit getter belongs to `Provider` and returns the configured value. An `UnsupportedOperationException` stub must not be used because it would fail every booking when the limit is checked.

### Rescheduling strategy

Disruption processing is asynchronous so the API remains responsive. `SchedulingStrategy` is the replacement boundary: deterministic rule-based matching is the reliable fallback, while the optional AI strategy is configured by `SLOTMIND_AI_URL`, `SLOTMIND_AI_TOKEN`, and `SLOTMIND_AI_MODEL`.

Provider-offline and slot-cancelled events mark confirmed appointments `RESCHEDULE_PENDING`. The agent creates at most three earliest valid suggestions. Rule-based suggestions require matching specialty; a `GENERAL` appointment must not be recommended to a specialist-only provider.

### Transactions and concurrency

Suggestion acceptance is atomic: the replacement slot is booked, the appointment is rescheduled, the old slot is released, and competing suggestions are rejected in one transaction. Optimistic locking prevents two users from accepting the same suggestion or booking the same slot. Conflicts return HTTP `409 Conflict` rather than silently overwriting state.

Patient cancellation marks the appointment cancelled and releases its booked slot in the same transaction. If the original provider recovers before acceptance, the appointment can be restored and pending suggestions are cleared.

### API errors

`com.slotmind.exception.ApiErrorHandler` is a `@RestControllerAdvice` that maps `IllegalArgumentException` to `400 Bad Request` and `IllegalStateException` to `409 Conflict`. Both use `{ "error": "..." }`, with safe fallback text for null or blank messages.

### Frontend feedback

The dashboard polls every four seconds and displays polling status. Initial loads use shimmer skeletons. Mutating actions lock and show spinners; successes use non-blocking notices and failures use error banners. Sticky glass headers, blurred modal overlays, independent schedule scrolling, suggestion highlight flashes, semantic badges, confidence/load bars, SSE reasoning, and patient-script copy feedback support staff workflows.

The frontend is not an authorization boundary; all availability and booking rules are rechecked by the backend.

## Tradeoffs

SQLite is simple and portable but serializes writes, so it is not the preferred production multi-user database. Async processing is responsive but eventually consistent, so polling is required. AI can improve explanations but is optional and cannot be allowed to decide correctness.

## Operations

Run `mvn spring-boot:run` in `backend`, and `npm run dev` in `frontend`. Actuator health is available at `/actuator/health`. Secrets must never be committed or embedded in a Git remote URL. Debug logs are controlled through `backend/src/main/resources/application.yml`.

## Validation

```powershell
cd backend
mvn clean test

cd ..\frontend
npm run build
```
