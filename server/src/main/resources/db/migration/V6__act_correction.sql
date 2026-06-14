-- act_correction: audit trail for Fe de erratas (ADR-0015).
-- The target locator (target_company_id + target_inscripcion) records what the
-- errata prose references. target_act_id is NULL until the target act is matched;
-- keeping the locator and the resolved id separate means an unmatched correction
-- still records what it points at and can be retried.
CREATE TABLE act_correction (
    id                 BIGSERIAL NOT NULL,
    errata_act_id      BIGINT    NOT NULL REFERENCES borme_act (id),
    target_company_id  BIGINT    NOT NULL REFERENCES company (id),
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
