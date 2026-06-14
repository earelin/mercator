-- staging_act: raw parsed rows loaded by the offline ingester before the SQL
-- merge resolves and upserts them into the live tables (ADR-0006).
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
    loaded_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed         BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_staging_act PRIMARY KEY (id)
);

-- borme_log: per-document processing record used for idempotency short-circuit
-- and resumable backfill (ADR-0006).
-- SKIPPED = non-publication day or 200-but-empty / no-Sección-A summary.
-- error_kind is set only when status = 'ERROR'.
-- source_path has no 'api_ingest' value — there is no HTTP ingest (ADR-0006).
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
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_borme_log PRIMARY KEY (borme_id)
);
