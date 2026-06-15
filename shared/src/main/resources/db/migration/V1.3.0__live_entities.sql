-- company: natural key is (reg_hoja, province_code).
-- NULL reg_hoja rows are never auto-deduped: PostgreSQL UNIQUE does not treat
-- NULLs as equal, so null-Hoja companies are create-and-flagged (ADR-0008).
CREATE TABLE company (
    id              BIGSERIAL NOT NULL,
    raw_name        TEXT      NOT NULL,
    norm_name       TEXT      NOT NULL,
    legal_form      TEXT,
    province_code   CHAR(2)   NOT NULL REFERENCES province (code),
    reg_hoja        TEXT,
    reg_tomo        TEXT,
    first_seen      DATE      NOT NULL,
    last_seen       DATE      NOT NULL,
    name_match_flag BOOLEAN   NOT NULL DEFAULT FALSE,
    suppressed      BOOLEAN   NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_company              PRIMARY KEY (id),
    CONSTRAINT uq_company_hoja_province UNIQUE (reg_hoja, province_code)
);

CREATE INDEX idx_company_norm_name ON company USING gin (norm_name gin_trgm_ops);

-- person: no stable identifier; probabilistic resolution only (ADR-0009).
CREATE TABLE person (
    id         BIGSERIAL NOT NULL,
    raw_name   TEXT      NOT NULL,
    norm_name  TEXT      NOT NULL,
    suppressed BOOLEAN   NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_person PRIMARY KEY (id)
);

CREATE INDEX idx_person_norm_name ON person USING gin (norm_name gin_trgm_ops);

-- address: deduped by (norm_text, province_code); shared address_id enables
-- deterministic shared-address link joins (Spec 5).
CREATE TABLE address (
    id            BIGSERIAL NOT NULL,
    raw_text      TEXT      NOT NULL,
    norm_text     TEXT      NOT NULL,
    municipality  TEXT,
    province_code CHAR(2)   NOT NULL REFERENCES province (code),
    CONSTRAINT pk_address              PRIMARY KEY (id),
    CONSTRAINT uq_address_norm_province UNIQUE (norm_text, province_code)
);

CREATE INDEX idx_address_norm_text ON address USING gin (norm_text gin_trgm_ops);
