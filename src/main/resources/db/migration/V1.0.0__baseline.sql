-- Mercator schema baseline (V1.0.0).
-- The complete database schema in one migration: extensions, reference data, live
-- entity tables, temporal links, the corrections audit trail, the staging/log tables
-- for the two write paths (ADR-0006), the derived/GDPR tables, and the temporal-interval
-- functions. From this baseline forward, migrations are versioned (semver x.x.x),
-- forward-only and immutable (ADR-0016).

-- ---------------------------------------------------------------------------
-- Extensions
-- ---------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS fuzzystrmatch;
CREATE EXTENSION IF NOT EXISTS unaccent;

-- ---------------------------------------------------------------------------
-- Reference data
-- ---------------------------------------------------------------------------
CREATE TABLE province (
    code CHAR(2) NOT NULL,
    name TEXT    NOT NULL,
    CONSTRAINT pk_province PRIMARY KEY (code)
);

INSERT INTO province (code, name) VALUES
    ('01', 'Álava'),
    ('02', 'Albacete'),
    ('03', 'Alicante'),
    ('04', 'Almería'),
    ('05', 'Ávila'),
    ('06', 'Badajoz'),
    ('07', 'Illes Balears'),
    ('08', 'Barcelona'),
    ('09', 'Burgos'),
    ('10', 'Cáceres'),
    ('11', 'Cádiz'),
    ('12', 'Castellón'),
    ('13', 'Ciudad Real'),
    ('14', 'Córdoba'),
    ('15', 'A Coruña'),
    ('16', 'Cuenca'),
    ('17', 'Girona'),
    ('18', 'Granada'),
    ('19', 'Guadalajara'),
    ('20', 'Gipuzkoa'),
    ('21', 'Huelva'),
    ('22', 'Huesca'),
    ('23', 'Jaén'),
    ('24', 'León'),
    ('25', 'Lleida'),
    ('26', 'La Rioja'),
    ('27', 'Lugo'),
    ('28', 'Madrid'),
    ('29', 'Málaga'),
    ('30', 'Murcia'),
    ('31', 'Navarra'),
    ('32', 'Ourense'),
    ('33', 'Asturias'),
    ('34', 'Palencia'),
    ('35', 'Las Palmas'),
    ('36', 'Pontevedra'),
    ('37', 'Salamanca'),
    ('38', 'Santa Cruz de Tenerife'),
    ('39', 'Cantabria'),
    ('40', 'Segovia'),
    ('41', 'Sevilla'),
    ('42', 'Soria'),
    ('43', 'Tarragona'),
    ('44', 'Teruel'),
    ('45', 'Toledo'),
    ('46', 'Valencia'),
    ('47', 'Valladolid'),
    ('48', 'Bizkaia'),
    ('49', 'Zamora'),
    ('50', 'Zaragoza'),
    ('51', 'Ceuta'),
    ('52', 'Melilla');

-- role: canonical cargo (appointment role) vocabulary.
-- Codes are the canonical enum that the parser/normaliser maps every BORME cargo spelling
-- variant onto (docs/specs/03-extraction.md §Roles,
-- docs/features/entity-extraction-normalisation.md). appointment.role references this table.
-- Seeded from the documented vocabulary; the bormeparser cargo-dictionary port (act-parsing.md)
-- may add further codes via a later additive migration.
CREATE TABLE role (
    code        TEXT NOT NULL,
    description TEXT NOT NULL,
    CONSTRAINT pk_role PRIMARY KEY (code)
);

INSERT INTO role (code, description) VALUES
    ('ADM_UNICO',     'Administrador único'),
    ('ADM_SOLIDARIO', 'Administrador solidario'),
    ('ADM_MANCOMUN',  'Administrador mancomunado'),
    ('CONSEJERO',     'Consejero'),
    ('PRESIDENTE',    'Presidente'),
    ('SECRETARIO',    'Secretario'),
    ('CONS_DEL_SOL',  'Consejero delegado solidario'),
    ('CON_DELEGADO',  'Consejero delegado'),
    ('APODERADO',     'Apoderado'),
    ('APO_SOL',       'Apoderado solidario'),
    ('APO_MANC',      'Apoderado mancomunado'),
    ('LIQUIDADOR',    'Liquidador'),
    ('LIQ_UNICO',     'Liquidador único'),
    ('AUDITOR',       'Auditor'),
    ('AUD_C_CON',     'Auditor de cuentas consolidadas'),
    ('SOCIO_UNICO',   'Socio único');

-- ---------------------------------------------------------------------------
-- Live entities
-- ---------------------------------------------------------------------------
-- company: natural key is (reg_hoja, province_code).
-- NULL reg_hoja rows are never auto-deduped: PostgreSQL UNIQUE does not treat
-- NULLs as equal, so null-Hoja companies are create-and-flagged (ADR-0008).
-- status tracks the lifecycle state derived from terminal BORME acts:
--   ACTIVE (default) — no terminal act yet.
--   DISSOLVED        — DISOLUCION act received; company in liquidation.
--   EXTINCT          — EXTINCION act received; legally struck off.
--   MERGED           — FUSION act received; absorbed by another company.
-- A REAPERTURA act resets status back to ACTIVE.
CREATE TABLE company (
    id              UUID      NOT NULL DEFAULT uuidv7(),
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
    status          TEXT      NOT NULL DEFAULT 'ACTIVE'
                        CHECK (status IN ('ACTIVE', 'DISSOLVED', 'EXTINCT', 'MERGED')),
    CONSTRAINT pk_company              PRIMARY KEY (id),
    CONSTRAINT uq_company_hoja_province UNIQUE (reg_hoja, province_code)
);

CREATE INDEX idx_company_norm_name ON company USING gin (norm_name gin_trgm_ops);

-- person: no stable identifier; probabilistic resolution only (ADR-0009).
CREATE TABLE person (
    id         UUID      NOT NULL DEFAULT uuidv7(),
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

-- ---------------------------------------------------------------------------
-- Acts
-- ---------------------------------------------------------------------------
CREATE TABLE borme_act (
    id                BIGSERIAL NOT NULL,
    borme_id          TEXT      NOT NULL,
    cve               TEXT,
    pub_date          DATE      NOT NULL,
    province_code     CHAR(2)   NOT NULL REFERENCES province (code),
    company_id        UUID      NOT NULL REFERENCES company (id),
    act_type          TEXT      NOT NULL,
    datos_registrales TEXT,
    inscripcion       TEXT,
    doc_seq           INTEGER   NOT NULL,
    raw_block         TEXT      NOT NULL,
    CONSTRAINT pk_borme_act PRIMARY KEY (id)
);

-- Functional unique index for idempotency (ADR-0006).
-- coalesce(datos_registrales, '') ensures two acts with NULL datos_registrales
-- and the same (borme_id, company_id, act_type, doc_seq) still collide, so
-- re-processing a document is a true no-op under ON CONFLICT DO NOTHING.
-- doc_seq is the per-document ordinal that distinguishes two legitimately
-- distinct acts of the same type sharing one datos_registrales (e.g. two ceses).
CREATE UNIQUE INDEX uq_borme_act_idempotency
    ON borme_act (borme_id, company_id, act_type,
                  coalesce(datos_registrales, ''), doc_seq);

CREATE INDEX idx_borme_act_company_id ON borme_act (company_id);

-- ---------------------------------------------------------------------------
-- Temporal links
-- ---------------------------------------------------------------------------
-- appointment: one row per tenure of a person in a role at a company.
-- valid_from = pub_date of the nombramiento; valid_to = pub_date of the cese,
-- or NULL while the appointment is current.
-- close_appointment_interval() sets valid_to when a cese is processed.
CREATE TABLE appointment (
    id         BIGSERIAL NOT NULL,
    company_id UUID      NOT NULL REFERENCES company (id),
    person_id  UUID      NOT NULL REFERENCES person (id),
    role       TEXT      NOT NULL,
    event_type TEXT      NOT NULL
                   CHECK (event_type IN ('NOMBRAMIENTO', 'CESE', 'REELECCION', 'REVOCACION')),
    act_id     BIGINT    NOT NULL REFERENCES borme_act (id),
    valid_from DATE      NOT NULL,
    valid_to   DATE,
    CONSTRAINT pk_appointment      PRIMARY KEY (id),
    CONSTRAINT fk_appointment_role FOREIGN KEY (role) REFERENCES role (code)
);

-- Partial index for close_appointment_interval(): UPDATE … WHERE
-- company_id=? AND person_id=? AND role=? AND valid_to IS NULL AND valid_from < ?
CREATE INDEX idx_appointment_open ON appointment (company_id, person_id, role)
    WHERE valid_to IS NULL;
-- Separate indexes for link queries that join on company or person alone.
CREATE INDEX idx_appointment_company_id ON appointment (company_id);
CREATE INDEX idx_appointment_person_id  ON appointment (person_id);

-- company_address: temporal domicilio history for a company.
-- close_company_address_interval() closes the previous interval when a new
-- domicilio is registered.
CREATE TABLE company_address (
    id         BIGSERIAL NOT NULL,
    company_id UUID      NOT NULL REFERENCES company (id),
    address_id BIGINT    NOT NULL REFERENCES address (id),
    act_id     BIGINT    REFERENCES borme_act (id),
    valid_from DATE      NOT NULL,
    valid_to   DATE,
    CONSTRAINT pk_company_address PRIMARY KEY (id)
);

-- Partial index for close_company_address_interval(): UPDATE … WHERE
-- company_id=? AND valid_to IS NULL AND valid_from < ?
CREATE INDEX idx_company_address_open ON company_address (company_id)
    WHERE valid_to IS NULL;
CREATE INDEX idx_company_address_company_id ON company_address (company_id);

-- ---------------------------------------------------------------------------
-- Corrections (Fe de erratas)
-- ---------------------------------------------------------------------------
-- act_correction: audit trail for Fe de erratas (ADR-0015).
-- The target locator (target_company_id + target_inscripcion) records what the
-- errata prose references. target_act_id is NULL until the target act is matched;
-- keeping the locator and the resolved id separate means an unmatched correction
-- still records what it points at and can be retried.
CREATE TABLE act_correction (
    id                 BIGSERIAL NOT NULL,
    errata_act_id      BIGINT    NOT NULL REFERENCES borme_act (id),
    target_company_id  UUID      NOT NULL REFERENCES company (id),
    target_inscripcion TEXT      NOT NULL,
    target_act_id      BIGINT    REFERENCES borme_act (id),
    field              TEXT      NOT NULL,
    old_value          TEXT,
    new_value          TEXT      NOT NULL,
    status             TEXT      NOT NULL DEFAULT 'UNAPPLIED'
                           CHECK (status IN ('APPLIED', 'UNAPPLIED')),
    flag_reason        TEXT,
    CONSTRAINT pk_act_correction PRIMARY KEY (id)
);

CREATE INDEX idx_act_correction_errata_act_id     ON act_correction (errata_act_id);
CREATE INDEX idx_act_correction_target_company_id ON act_correction (target_company_id);
-- Partial: target_act_id is NULL until matched; only index non-NULL rows.
CREATE INDEX idx_act_correction_target_act_id     ON act_correction (target_act_id)
    WHERE target_act_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- Write-path staging & log (ADR-0006)
-- ---------------------------------------------------------------------------
-- staging_act: raw parsed rows bulk-loaded by the historical import before the
-- SQL merge resolves and upserts them into the live tables (ADR-0006).
-- No FKs or resolution here — the parser never resolves identity (ADR-0007).
CREATE TABLE staging_act (
    id                BIGSERIAL   NOT NULL,
    borme_id          TEXT        NOT NULL,
    company_raw_name  TEXT        NOT NULL,
    company_norm_name TEXT        NOT NULL,
    legal_form        TEXT,
    province_code     CHAR(2)     NOT NULL,
    reg_hoja          TEXT,
    reg_tomo          TEXT,
    act_type          TEXT        NOT NULL,
    datos_registrales TEXT,
    inscripcion       TEXT,
    doc_seq           INTEGER     NOT NULL,
    domicilio_raw     TEXT,
    domicilio_norm    TEXT,
    municipality      TEXT,
    appointments      JSONB,
    loaded_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed         BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_staging_act PRIMARY KEY (id)
);

-- borme_log: per-document processing record used for idempotency short-circuit
-- and resumable backfill (ADR-0006).
-- SKIPPED = non-publication day or 200-but-empty / no-Sección-A summary.
-- error_kind is set only when status = 'ERROR'.
-- source_path distinguishes the two write paths; there is no public HTTP ingest.
CREATE TABLE borme_log (
    borme_id     TEXT        NOT NULL,
    pub_date     DATE        NOT NULL,
    status       TEXT        NOT NULL
                     CHECK (status IN ('FETCHED', 'PARSED', 'MERGED', 'SKIPPED', 'ERROR')),
    error_kind   TEXT
                     CHECK (error_kind IS NULL OR error_kind IN ('RETRYABLE', 'PERMANENT')),
    source_path  TEXT        NOT NULL
                     CHECK (source_path IN ('backfill', 'daily_incremental')),
    error_detail TEXT,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_borme_log PRIMARY KEY (borme_id)
);

-- ---------------------------------------------------------------------------
-- Derived & GDPR tables
-- ---------------------------------------------------------------------------
-- match_candidate: ranked name+province matches between Mercator companies and
-- external awardees. Written by an internal batch matcher; the contracts project
-- only reads it (no write coupling). Not part of the BORME ingestion write paths
-- (ADR-0006).
CREATE TABLE match_candidate (
    id              BIGSERIAL    NOT NULL,
    company_id      UUID         NOT NULL REFERENCES company (id),
    external_ref    TEXT         NOT NULL,
    external_source TEXT         NOT NULL,
    score           NUMERIC(5,4) NOT NULL CHECK (score BETWEEN 0.0 AND 1.0),
    computed_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_match_candidate PRIMARY KEY (id),
    CONSTRAINT uq_match_candidate UNIQUE (company_id, external_ref, external_source)
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
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
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
    at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_erasure_log PRIMARY KEY (id)
);

-- ---------------------------------------------------------------------------
-- Temporal-interval functions
-- ---------------------------------------------------------------------------
-- Closes the open appointment interval(s) for a (company, person, role) triple.
-- Called by IngestionService before inserting a new NOMBRAMIENTO (to close the
-- prior open tenure) and when processing a CESE or REVOCACION event.
CREATE OR REPLACE FUNCTION close_appointment_interval(
    p_company_id UUID,
    p_person_id  UUID,
    p_role       TEXT,
    p_close_date DATE
) RETURNS VOID LANGUAGE plpgsql AS $$
BEGIN
    UPDATE appointment
       SET valid_to = p_close_date
     WHERE company_id = p_company_id
       AND person_id  = p_person_id
       AND role       = p_role
       AND valid_to   IS NULL
       AND valid_from < p_close_date;
END;
$$;

-- Closes all open company_address intervals for a company.
-- Called by IngestionService before inserting a new domicilio so the previous
-- registered address interval is properly terminated.
CREATE OR REPLACE FUNCTION close_company_address_interval(
    p_company_id UUID,
    p_close_date DATE
) RETURNS VOID LANGUAGE plpgsql AS $$
BEGIN
    UPDATE company_address
       SET valid_to = p_close_date
     WHERE company_id = p_company_id
       AND valid_to   IS NULL
       AND valid_from < p_close_date;
END;
$$;
