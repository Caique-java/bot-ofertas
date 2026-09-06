-- New tables deliberately do not overwrite legacy promocao_historico.
CREATE TABLE bot_guard (id integer PRIMARY KEY);
INSERT INTO bot_guard VALUES (1);
CREATE TABLE source_state (
    source varchar(40) PRIMARY KEY,
    next_request timestamptz NOT NULL DEFAULT '1970-01-01',
    next_poll timestamptz NOT NULL DEFAULT '1970-01-01',
    last_success timestamptz,
    last_error varchar(80),
    failures integer NOT NULL DEFAULT 0,
    evaluated bigint NOT NULL DEFAULT 0
);
CREATE TABLE offer_queue (
    id varchar(36) PRIMARY KEY,
    offer_key varchar(64) NOT NULL,
    marketplace varchar(20) NOT NULL,
    channel varchar(100) NOT NULL,
    dry_run boolean NOT NULL,
    payload text,
    price numeric(14,2),
    reference_price numeric(14,2),
    priority numeric(6,2) NOT NULL,
    reason varchar(80) NOT NULL,
    repost_reason varchar(80) NOT NULL,
    status varchar(20) NOT NULL CHECK (status IN ('PENDING','CHECKING','SENDING','SENT','PREVIEW','FAILED','EXPIRED','UNCERTAIN')),
    attempts integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    next_attempt timestamptz NOT NULL,
    lease_until timestamptz,
    claim_token varchar(36),
    message_id bigint,
    completed_at timestamptz,
    last_error varchar(80)
);
CREATE UNIQUE INDEX uq_offer_active ON offer_queue(channel,dry_run,offer_key)
    WHERE status IN ('PENDING','CHECKING','SENDING','UNCERTAIN');
CREATE INDEX ix_queue_due ON offer_queue(channel,dry_run,status,next_attempt,priority DESC);
CREATE INDEX ix_queue_history ON offer_queue(channel,dry_run,offer_key,completed_at DESC);
CREATE TABLE channel_state (
    channel varchar(100) PRIMARY KEY,
    next_send timestamptz NOT NULL DEFAULT '1970-01-01'
);
CREATE TABLE publication_attempt (
    id varchar(36) PRIMARY KEY,
    job_id varchar(36) NOT NULL REFERENCES offer_queue(id) ON DELETE CASCADE,
    channel varchar(100) NOT NULL,
    started_at timestamptz NOT NULL,
    result varchar(40) NOT NULL,
    message_id bigint
);
CREATE INDEX ix_attempt_volume ON publication_attempt(channel,started_at);
CREATE TABLE price_observation (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    offer_key varchar(64) NOT NULL,
    price numeric(14,2) NOT NULL,
    observed_at timestamptz NOT NULL
);
CREATE INDEX ix_observation_history ON price_observation(offer_key,observed_at);
