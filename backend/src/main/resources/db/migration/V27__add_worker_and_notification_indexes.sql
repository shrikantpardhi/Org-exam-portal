-- ---------------------------------------------------------------------------
-- V27 - composite worker and notification indexes (ADR-0028, GAP-139)
--
-- The frozen index list (specs/04-Database-Schema-V1.5.md §33.1, §35.1) declares
-- only single-column indexes for these two tables. Two hot queries filter on two
-- columns and order by a third, which a single-column index cannot serve:
--
--   background_job: WHERE job_type = ? AND status = ? ORDER BY queued_on ASC
--   notification:   WHERE user_id = ? AND notification_status = ?
--                   ORDER BY created_on DESC
--
-- These are additional indexes. No column, constraint or existing index is
-- modified, and no data is rewritten. The frozen single-column indexes are left
-- in place: they still serve the other predicates (admin counts, per-user reads
-- without a status filter, report history).
--
-- Sort direction is intentionally left ASC. PostgreSQL can scan an index
-- backwards at no cost, so (..., created_on) serves `ORDER BY created_on DESC`
-- exactly as well as it serves ASC, and one index covers both.
--
-- Rollback (reversible; ADR-0028 §"Rollback"):
--   DROP INDEX IF EXISTS idx_background_job_type_status_queued;
--   DROP INDEX IF EXISTS idx_notification_user_status_created;
--
-- Created without CONCURRENTLY because Flyway runs each migration inside a
-- transaction and CONCURRENTLY cannot run in one. Both tables are small at this
-- point in the project's life, so the brief write lock is immaterial; ADR-0028
-- records the separate procedure for building them on a large production table.
-- ---------------------------------------------------------------------------

CREATE INDEX idx_background_job_type_status_queued
    ON background_job (job_type, status, queued_on);

CREATE INDEX idx_notification_user_status_created
    ON notification (user_id, notification_status, created_on);
