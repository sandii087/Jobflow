CREATE TABLE users (
 id UUID PRIMARY KEY, email VARCHAR(320) NOT NULL UNIQUE, password_hash VARCHAR(100) NOT NULL, role VARCHAR(16) NOT NULL CHECK (role IN ('USER','ADMIN')), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE jobs (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL REFERENCES users(id), type VARCHAR(32) NOT NULL CHECK (type IN ('REPORT_GENERATION','EMAIL_NOTIFICATION','DATA_PROCESSING')), payload JSONB NOT NULL,
 priority VARCHAR(8) NOT NULL CHECK (priority IN ('HIGH','MEDIUM','LOW')), status VARCHAR(16) NOT NULL CHECK (status IN ('QUEUED','PROCESSING','RETRY_WAIT','COMPLETED','FAILED','DEAD_LETTER','CANCELLED')),
 available_at TIMESTAMPTZ NOT NULL DEFAULT now(), lease_expires_at TIMESTAMPTZ, attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0), max_attempts INTEGER NOT NULL DEFAULT 3 CHECK (max_attempts BETWEEN 1 AND 20),
 result JSONB, error_code VARCHAR(64), error_message VARCHAR(1000), idempotency_key VARCHAR(128), created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT jobs_owner_idempotency_unique UNIQUE(owner_id, idempotency_key)
);
CREATE INDEX jobs_claim_idx ON jobs (available_at, created_at) WHERE status IN ('QUEUED','RETRY_WAIT');
CREATE INDEX jobs_owner_created_idx ON jobs (owner_id, created_at DESC);
CREATE INDEX jobs_status_created_idx ON jobs (status, created_at DESC);
CREATE TABLE job_attempts (id UUID PRIMARY KEY, job_id UUID NOT NULL REFERENCES jobs(id), attempt_number INTEGER NOT NULL, started_at TIMESTAMPTZ NOT NULL, finished_at TIMESTAMPTZ, outcome VARCHAR(16), error_message VARCHAR(1000), UNIQUE(job_id, attempt_number));
CREATE INDEX job_attempts_job_idx ON job_attempts(job_id, attempt_number);
CREATE TABLE rate_limit_buckets (subject VARCHAR(360) NOT NULL, window_start TIMESTAMPTZ NOT NULL, request_count INTEGER NOT NULL CHECK(request_count >= 0), PRIMARY KEY(subject, window_start));
