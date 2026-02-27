# To-Do Service

A RESTful backend service for managing a simple to-do list. Built with Java 21 and Spring Boot 3.

---

## Service Description

The service exposes a REST API to create, update, and query to-do items. Each item has a description, a due date, and a status (`NOT_DONE`, `DONE`, or `PAST_DUE`). Items whose due date has passed are automatically transitioned to `PAST_DUE`. Once an item is `PAST_DUE` or `DONE`, it is immutable — no further mutations are accepted.

---

## Assumptions

- All timestamps are stored and returned in **UTC** (ISO 8601 format, e.g. `2026-03-01T14:00:00Z`). Clients are responsible for timezone conversion.
- A to-do item with a `dueAt` in the past **cannot be created**. The service is not a historical log.
- Once an item is marked `DONE`, it is **immutable**. The `doneAt` field serves as an audit record and cannot be unset.
- `PAST_DUE` items are fully immutable — they cannot be marked done, reverted, or have their description changed.
- The scheduler sweep interval defaults to **60 seconds** and is configurable via `TODO_SCHEDULER_PAST_DUE_SWEEP_DELAY_MS`.
- No authentication or multi-tenancy is implemented. All items are globally accessible.
- Description is limited to **1000 characters**. Empty or whitespace-only descriptions are rejected.
- The H2 database is **in-memory only**. All data is lost on restart. This is intentional per the requirements.
- `dueAt` is mandatory on creation. There is no concept of a to-do without a deadline in this system.

---

## Tech Stack

| Component        | Technology                              |
|------------------|-----------------------------------------|
| Runtime          | Java 21                                 |
| Framework        | Spring Boot 3.2                         |
| Persistence      | Spring Data JPA + H2 (in-memory)        |
| Build tool       | Maven 3.9                               |
| Logging          | SLF4J + Logback + Logstash JSON encoder |
| Containerisation | Docker (multi-stage) + docker-compose   |
| Testing          | JUnit 5, Mockito, Spring Boot Test, MockMvc |

---

## Architecture & Design Decisions

### Past-Due Detection: Dual-Layer Approach
Two mechanisms work together to ensure items are always shown with accurate status:

1. **`@Scheduled` background sweep** (every 60s): Runs a single bulk `UPDATE` SQL query to flip all overdue `NOT_DONE` items to `PAST_DUE`. Efficient at any data volume — no entities are loaded into memory.
2. **On-read guard**: Before returning an item (in `getById` or `getAll`), the service checks if a `NOT_DONE` item's `dueAt` has passed and corrects it immediately. This handles the ≤60s window between sweeps.

### Immutability Contract
Both `DONE` and `PAST_DUE` items are fully locked from mutation. This keeps the state machine simple and preserves the `doneAt` audit trail. Attempting to mutate a locked item returns `409 Conflict`.

### Concurrency: Optimistic Locking
The `TodoItem` entity includes a JPA `@Version` field. Concurrent `PATCH` requests on the same item will result in one succeeding and the other receiving a `409 Conflict` response, without any heavyweight pessimistic locking.

### UUID Primary Keys
Items use UUID identifiers rather than auto-increment integers. This avoids leaking record counts through sequential IDs and is safe to expose in URLs.

### Centralized Error Handling
All exceptions are caught by a single `GlobalExceptionHandler` (`@RestControllerAdvice`). Every error response follows the same JSON shape. No error handling logic lives in the controllers.

### Structured Logging
All log lines are enriched with a `traceId` (a UUID unique to each HTTP request) via SLF4J MDC. The `traceId` is also returned to the client as the `X-Trace-Id` response header. JSON-formatted logs are written to `logs/todo-service.log` for log aggregator ingestion.

---

## How to Build

```bash
# Compile and package (skip tests)
mvn clean package -DskipTests

# Compile and package (with tests)
mvn clean package
```

---

## How to Run Automatic Tests

```bash
# Run all tests (unit + repository + integration)
mvn test

# Run a specific test class
mvn test -Dtest=TodoServiceTest
mvn test -Dtest=TodoRepositoryTest
mvn test -Dtest=TodoControllerIntegrationTest
```

---

## How to Run Locally

### Option 1: Maven

```bash
mvn spring-boot:run
```

Service starts on `http://localhost:8080`.  
H2 console available at `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:tododb`).

### Option 2: Docker Compose

```bash
# Build and start
docker-compose up --build

# Stop
docker-compose down
```

JSON logs are written to `./logs/todo-service.log` on the host (volume-mounted from the container).

### Option 3: Docker only

```bash
docker build -t todo-service .
docker run -p 8080:8080 todo-service
```

---

## API Reference

### Base URL
```
http://localhost:8080/api/v1/todos
```

### Endpoints

| Method | Path                          | Description                                     |
|--------|-------------------------------|-------------------------------------------------|
| POST   | `/api/v1/todos`               | Create a new to-do item                         |
| GET    | `/api/v1/todos`               | Get NOT_DONE items (add `?all=true` for all)    |
| GET    | `/api/v1/todos/{id}`          | Get details of a specific item                  |
| PATCH  | `/api/v1/todos/{id}/done`     | Mark item as DONE                               |
| PATCH  | `/api/v1/todos/{id}/not-done` | Revert item to NOT_DONE (only if NOT_DONE)      |
| PATCH  | `/api/v1/todos/{id}/description` | Update item description (only if NOT_DONE)  |

### Error Response Shape
All errors return the same JSON structure:
```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Todo item <id> is PAST_DUE and cannot be modified.",
  "path": "/api/v1/todos/<id>/done",
  "traceId": "a3f7c891-...",
  "timestamp": "2026-02-26T10:05:00Z"
}
```

---

## curl Examples

### Create a to-do item
```bash
curl -X POST http://localhost:8080/api/v1/todos \
  -H "Content-Type: application/json" \
  -d '{"description":"Buy milk","dueAt":"2026-03-01T14:00:00Z"}'
```

### Get all NOT_DONE items
```bash
curl http://localhost:8080/api/v1/todos
```

### Get all items (including DONE and PAST_DUE)
```bash
curl "http://localhost:8080/api/v1/todos?all=true"
```

### Get a specific item
```bash
curl http://localhost:8080/api/v1/todos/{id}
```

### Mark as done
```bash
curl -X PATCH http://localhost:8080/api/v1/todos/{id}/done
```

### Mark as not done
```bash
curl -X PATCH http://localhost:8080/api/v1/todos/{id}/not-done
```

### Update description
```bash
curl -X PATCH http://localhost:8080/api/v1/todos/{id}/description \
  -H "Content-Type: application/json" \
  -d '{"description":"Buy oat milk"}'
```

---

## Logging

| Appender      | Format           | Location                    | Use Case                        |
|---------------|------------------|-----------------------------|---------------------------------|
| CONSOLE       | Human-readable   | stdout                      | Local development               |
| FILE_JSON     | JSON (Logstash)  | `logs/todo-service.log`     | Log aggregator (ELK, Datadog)   |

Every log line includes `traceId`, `httpMethod`, and `requestPath` from MDC.  
The `traceId` is returned in the `X-Trace-Id` response header for client correlation.
