-- ---------------------------------------------------------------------------
-- V1 - base schema (P0 foundation)
--
-- P0 proves the migration mechanism only. It creates no business tables.
-- Business tables are added by their corresponding phases:
--   P1 identity, P2 academic, P3 content, P4 assessment, P5 delivery,
--   P6 evaluation, P8 analytics, P9 commerce, P10 communication,
--   P11 administration, P13 reporting-related.
--
-- The pg_trgm extension supports the PostgreSQL-backed search required by the
-- technical design (specs/03-Technical-Design-Document-V1.5.md §78).
-- ---------------------------------------------------------------------------

CREATE EXTENSION IF NOT EXISTS pg_trgm;
