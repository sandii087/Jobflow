# JobFlow

JobFlow is a portfolio-grade, durable asynchronous job-processing platform. It is a modular Spring Boot application, not a claim of production traffic, availability, or customers.

## Architecture

```text
Client -> REST API (JWT, validation, PostgreSQL-backed rate limit)
                 -> PostgreSQL: users, jobs, attempts, rate-limit buckets
Worker pool <---- PostgreSQL durable queue / leasing
                 -> job executors -> result, retry, or dead letter state
```

PostgreSQL is deliberately both the system of record and the work queue. For this project it removes dual-write and broker-operational concerns while retaining safe coordination across application instances. It is suitable for modest queue throughput; a dedicated broker can be introduced later if independently scalable API/worker throughput is needed.

## Job lifecycle and reliability

`QUEUED|RETRY_WAIT -> PROCESSING -> COMPLETED`. Failures become `RETRY_WAIT` with 1s, 2s, 4s… exponential backoff (capped), or `DEAD_LETTER` when permanent/exhausted. Cancellation is valid only before processing. Admins can replay dead-letter jobs, which resets its attempt count but retains its original idempotency identity.

Workers atomically choose eligible rows with PostgreSQL `SELECT … FOR UPDATE SKIP LOCKED`, transition them to `PROCESSING`, increment attempts, and set a lease in the same database transaction. Leases are reclaimed by a scheduled recovery task after expiry, protecting against worker process crashes. This provides **at-least-once execution**, not exactly once: a worker may complete external work then crash before persisting completion. Executors must therefore make side effects idempotent using the job UUID.

Priority uses an aging score: HIGH starts ahead of MEDIUM and LOW, but each minute of wait time increases every job's score. Low priority therefore eventually outranks a new HIGH-priority job; the trade-off is bounded latency rather than absolute strict priority.

## API

OpenAPI UI: `http://localhost:8080/swagger-ui.html`. Register at `POST /api/auth/register`, then authenticate with `Authorization: Bearer <token>`.

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/jobs` | Submit a job; mandatory `Idempotency-Key` header |
| GET | `/api/jobs` | List caller's jobs, pageable and sortable |
| GET | `/api/jobs/{id}` | Read caller's job |
| POST | `/api/jobs/{id}/cancel` | Cancel queued/retry-wait job |
| GET | `/api/jobs/{id}/result` | Read result/status |
| GET/POST | `/api/admin/*` | ADMIN-only inspection and DLQ replay |

Job payloads are JSON, limited to 64KiB by default. Supported types are `REPORT_GENERATION`, `EMAIL_NOTIFICATION`, and `DATA_PROCESSING`; the demo executors do not invoke shell commands. `simulateFailure: transient|permanent` is available only to demonstrate retry paths.

## Security and operations

Passwords use BCrypt cost 12. JWTs are signed with a mandatory 32+ byte secret supplied through `JWT_SECRET`; never commit it. Authorization is service and endpoint scoped: users query only their own jobs; admin routes require `ADMIN`. The API does not log passwords/tokens/payloads. Submission limits use one atomic PostgreSQL upsert per user/minute, so multiple API instances share the same counter and return HTTP 429 after the configured limit. Health and Prometheus endpoints are available through Actuator; detailed actuator data is admin-protected. An ADMIN user must be provisioned directly through a controlled database/bootstrap process; public registration intentionally creates only USER accounts.

Database migrations are Flyway-managed. Key schema protections include UUID keys, foreign keys, enum check constraints, idempotency uniqueness on `(owner_id, idempotency_key)`, partial eligible-job indexing, owner lookup indexing, and attempt history uniqueness.

## Run and test

```bash
export JWT_SECRET='replace-with-a-long-random-development-secret'
mvn verify
mvn package
docker compose up --build
```

Docker Compose starts PostgreSQL only after health checks pass. The multi-stage Dockerfile compiles its own JAR, so `docker compose up --build` works from a clean checkout. CI runs Maven verification (including Testcontainers tests) and an image build. This workstation did not have Docker available during implementation, so Compose and container-backed integration checks must be run in Docker-enabled CI/local environment before any deployment claim.

## Deployment

`render.yaml` is a reproducible Render Blueprint: it provisions a managed PostgreSQL database plus two Docker services. `jobflow-api` is the public HTTP API with workers disabled, and `jobflow-worker` runs the same image with workers enabled; both use the same database-backed queue. The Blueprint supplies database connection components as provider-managed environment variables and has Render generate the JWT signing secret—no production secret belongs in Git.

To deploy, import the repository through the [Render Blueprint flow](https://dashboard.render.com/blueprint/new?repo=https://github.com/sandii087/Jobflow), review the proposed resources and costs, and apply it. Render runs Flyway on application startup; use `/actuator/health/readiness` as the API health check. A rollback means redeploying a previously known-good Git commit; Flyway migrations are forward-only, while in-flight jobs are recovered by expired leases. This workspace has not authenticated to Render and has no Docker daemon, so neither a cloud deployment nor Compose execution is claimed as verified.

## Failure modes and limits

Database outage makes submission and claiming unavailable; jobs remain durable once committed. There is no independent broker, so no broker outage mode. An API crash before commit creates no job; after commit, idempotency safely returns the existing job. A worker crash triggers lease recovery. Long tasks must finish within the configured lease or add executor heartbeats/lease renewal (not yet implemented). Rate-limit buckets have no cleanup job yet. For 100K+/high-throughput workloads, add partitioning, cleanup/archival, per-type pools, lease heartbeats, and evaluate an outbox plus dedicated broker. Multi-region needs a single writer region or explicit conflict/routing strategy.

## Interview prompts (implementation-specific)

**Why PostgreSQL instead of a broker?** Jobs, idempotency, state, and attempts need a single transactional source of truth; `SKIP LOCKED` supplies concurrent worker coordination without a dual-write. The cost is that very high queue throughput would push toward a broker.

**How are duplicate claims prevented?** Candidate selection locks a row in PostgreSQL and changes it to `PROCESSING` before the transaction commits. Other workers skip locked rows; the status predicate also excludes committed claims.

**What if a worker crashes?** Its `lease_expires_at` passes, then recovery changes it to `RETRY_WAIT`; it will be picked up again. Thus the guarantee is at-least-once and external effects must be idempotent.

**What is idempotency here?** A unique database constraint on owner plus key makes concurrent retries resolve to one durable job. The service catches the losing insert race and returns the winner.

**Why backoff/DLQ?** Exponential waits limit repeated pressure during transient incidents. Permanent/exhausted failures retain error and attempt history in `DEAD_LETTER` for safe admin investigation/replay.

## Resume bullets

- Built JobFlow, a Spring Boot/PostgreSQL asynchronous processing platform with JWT-scoped job APIs, Flyway migrations, and OpenAPI documentation.
- Implemented database-backed idempotent submission and multi-worker job claiming using PostgreSQL row locks and `SKIP LOCKED`.
- Designed at-least-once execution with leases, stale-worker recovery, exponential retry, attempt history, and dead-letter replay.
- Added container/CI configuration, Actuator/Prometheus observability, input limits, and PostgreSQL-backed multi-instance rate limiting.
