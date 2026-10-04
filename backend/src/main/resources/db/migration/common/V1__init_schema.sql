-- Spec 4.1, 4.3: all three tables and their indexes.

CREATE TABLE flag_group (
    id          UUID          NOT NULL,
    key         VARCHAR(50)   NOT NULL,
    name        VARCHAR(100)  NOT NULL,
    description VARCHAR(500),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by  VARCHAR(100)  NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    updated_by  VARCHAR(100)  NOT NULL,
    version     BIGINT        NOT NULL,
    CONSTRAINT pk_flag_group PRIMARY KEY (id),
    CONSTRAINT uk_flag_group_key UNIQUE (key)
);

CREATE TABLE feature_flag (
    id          UUID          NOT NULL,
    group_id    UUID          NOT NULL,
    key         VARCHAR(50)   NOT NULL,
    description VARCHAR(500),
    enabled     BOOLEAN       NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ   NOT NULL,
    created_by  VARCHAR(100)  NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    updated_by  VARCHAR(100)  NOT NULL,
    version     BIGINT        NOT NULL,
    CONSTRAINT pk_feature_flag PRIMARY KEY (id),
    CONSTRAINT fk_feature_flag_group FOREIGN KEY (group_id) REFERENCES flag_group (id) ON DELETE CASCADE,
    CONSTRAINT uk_feature_flag_group_key UNIQUE (group_id, key)
);

CREATE INDEX idx_feature_flag_group_id ON feature_flag (group_id);

-- Append-only history. No foreign keys, so history survives deletions (spec 4.1).
CREATE TABLE audit_event (
    id          BIGSERIAL     NOT NULL,
    occurred_at TIMESTAMPTZ   NOT NULL,
    actor       VARCHAR(100)  NOT NULL,
    action      VARCHAR(30)   NOT NULL,
    target_key  VARCHAR(101)  NOT NULL,
    details     JSONB,
    CONSTRAINT pk_audit_event PRIMARY KEY (id)
);

-- GET /audit sorts by occurred_at DESC, then id DESC (spec 6.1).
CREATE INDEX idx_audit_event_occurred_at ON audit_event (occurred_at DESC, id DESC);
-- Prefix filter on targetKey (spec 6.1 GET /audit).
CREATE INDEX idx_audit_event_target_key ON audit_event (target_key varchar_pattern_ops);
