-- company.status tracks the lifecycle state derived from terminal BORME acts.
-- ACTIVE  (default) — no terminal act yet.
-- DISSOLVED          — DISOLUCION act received; company in liquidation.
-- EXTINCT            — EXTINCION act received; legally struck off.
-- MERGED             — FUSION act received; absorbed by another company.
-- A REAPERTURA act resets status back to ACTIVE.
ALTER TABLE company
    ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'DISSOLVED', 'EXTINCT', 'MERGED'));
