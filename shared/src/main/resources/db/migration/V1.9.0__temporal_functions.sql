-- Closes the open appointment interval(s) for a (company, person, role) triple.
-- Called by IngestionService before inserting a new NOMBRAMIENTO (to close the
-- prior open tenure) and when processing a CESE or REVOCACION event.
CREATE OR REPLACE FUNCTION close_appointment_interval(
    p_company_id BIGINT,
    p_person_id  BIGINT,
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
    p_company_id BIGINT,
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
