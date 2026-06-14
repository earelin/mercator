-- match_candidate: ranked name+province matches between Mercator companies and
-- external awardees. Written by an internal batch matcher; the contracts project
-- only reads it (no write coupling). Not part of the BORME ingestion write paths
-- (ADR-0006).
CREATE TABLE match_candidate (
    id              BIGSERIAL    NOT NULL,
    company_id      BIGINT       NOT NULL REFERENCES company (id),
    external_ref    TEXT         NOT NULL,
    external_source TEXT         NOT NULL,
    score           NUMERIC(5,4) NOT NULL CHECK (score BETWEEN 0.0 AND 1.0),
    computed_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_match_candidate   PRIMARY KEY (id),
    CONSTRAINT uq_match_candidate   UNIQUE (company_id, external_ref, external_source)
);

-- suppression: GDPR erasure/suppression decisions, keyed independently of act
-- rows so they survive re-ingestion and the backfill→merge rebuild (Spec 7).
-- subject_ref may be a salted hash of a DNI/NIE or a person_id string.
CREATE TABLE suppression (
    id           BIGSERIAL   NOT NULL,
    subject_type TEXT        NOT NULL
                     CHECK (subject_type IN ('person', 'company', 'identifier')),
    subject_ref  TEXT        NOT NULL,
    reason       TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actor        TEXT        NOT NULL,
    CONSTRAINT pk_suppression PRIMARY KEY (id),
    CONSTRAINT uq_suppression UNIQUE (subject_type, subject_ref)
);

-- erasure_log: append-only audit of every suppression/erasure request and
-- the decision taken; retained per the retention policy (Spec 7).
CREATE TABLE erasure_log (
    id          BIGSERIAL   NOT NULL,
    request     TEXT        NOT NULL,
    decision    TEXT        NOT NULL,
    subject_ref TEXT        NOT NULL,
    actor       TEXT        NOT NULL,
    at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_erasure_log PRIMARY KEY (id)
);
