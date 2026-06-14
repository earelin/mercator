-- appointment: one row per tenure of a person in a role at a company.
-- valid_from = pub_date of the nombramiento; valid_to = pub_date of the cese,
-- or NULL while the appointment is current.
-- close_appointment_interval() (V9) sets valid_to when a cese is processed.
CREATE TABLE appointment (
    id         BIGSERIAL NOT NULL,
    company_id BIGINT    NOT NULL REFERENCES company (id),
    person_id  BIGINT    NOT NULL REFERENCES person (id),
    role       TEXT      NOT NULL,
    event_type TEXT      NOT NULL
                   CHECK (event_type IN ('NOMBRAMIENTO', 'CESE', 'REELECCION', 'REVOCACION')),
    act_id     BIGINT    NOT NULL REFERENCES borme_act (id),
    valid_from DATE      NOT NULL,
    valid_to   DATE,
    CONSTRAINT pk_appointment PRIMARY KEY (id)
);

-- company_address: temporal domicilio history for a company.
-- close_company_address_interval() (V9) closes the previous interval when a
-- new domicilio is registered.
CREATE TABLE company_address (
    id         BIGSERIAL NOT NULL,
    company_id BIGINT    NOT NULL REFERENCES company (id),
    address_id BIGINT    NOT NULL REFERENCES address (id),
    act_id     BIGINT    REFERENCES borme_act (id),
    valid_from DATE      NOT NULL,
    valid_to   DATE,
    CONSTRAINT pk_company_address PRIMARY KEY (id)
);
