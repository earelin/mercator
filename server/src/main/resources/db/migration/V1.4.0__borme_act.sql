CREATE TABLE borme_act (
    id                BIGSERIAL NOT NULL,
    borme_id          TEXT      NOT NULL,
    cve               TEXT,
    pub_date          DATE      NOT NULL,
    province_code     CHAR(2)   NOT NULL REFERENCES province (code),
    company_id        BIGINT    NOT NULL REFERENCES company (id),
    act_type          TEXT      NOT NULL,
    datos_registrales TEXT,
    inscripcion       TEXT,
    doc_seq           INTEGER   NOT NULL,
    raw_block         TEXT      NOT NULL,
    CONSTRAINT pk_borme_act PRIMARY KEY (id)
);

-- Functional unique index for idempotency (ADR-0006).
-- COALESCE(datos_registrales, '') ensures two acts with NULL datos_registrales
-- and the same (borme_id, company_id, act_type, doc_seq) still collide, so
-- re-processing a document is a true no-op under ON CONFLICT DO NOTHING.
-- doc_seq is the per-document ordinal that distinguishes two legitimately
-- distinct acts of the same type sharing one datos_registrales (e.g. two ceses).
CREATE UNIQUE INDEX uq_borme_act_idempotency
    ON borme_act (borme_id, company_id, act_type,
                  COALESCE(datos_registrales, ''), doc_seq);

CREATE INDEX idx_borme_act_company_id ON borme_act (company_id);
