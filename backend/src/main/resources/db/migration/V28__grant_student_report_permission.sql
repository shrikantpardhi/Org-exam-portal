-- ---------------------------------------------------------------------------
-- V28 - Grant REPORT_STUDENT_READ to STUDENT (GAP-142, ADR-0030)
--
-- V24 created the eight report permissions and granted them to ADMIN, STAFF and FINANCE, closing with
-- a comment stating that STUDENT deliberately receives no report permission because "a student's
-- permission set is empty".
--
-- That comment stated the constraint more broadly than the frozen contracts do. What they actually
-- protect is that a student gains no cross-user, operational, executive, assessment, content or
-- commerce reporting capability:
--
--   * specs/10-P1-Identity-V1.5.md §9 says "Do not encode business authorization as
--     if role == ADMIN ... Prefer permission checks."
--   * specs/22-P13-Reporting-V1.5.md §74 and §76 already assume the permission exists: the catalog
--     filters entries by hasPermission(entry.permission()), and the student report types declare
--     REPORT_STUDENT_READ.
--   * The invariant tests forbid SPECIFIC codes for STUDENT (SUPPORT_*, GLOBAL_SEARCH*,
--     AUDIT_LOG_VIEW, and the operational/executive/commerce/assessment/content report permissions).
--     Neither asserts that the student set is empty, and neither mentions REPORT_STUDENT_READ.
--
-- Granting exactly this one permission is not a scope widening. ReportController.isStudent treats any
-- caller without REPORT_OPERATIONS_READ and REPORT_EXECUTIVE_READ as a student, and
-- ReportService.scopedFilters forces studentId = callerId before any provider runs; ReportCriteria
-- .scopedSubject applies the same rule again at projection time. There is no code path that would
-- honour a student filter pointing at another user.
--
-- What is NOT granted: REPORT_ASSESSMENT_READ, REPORT_CONTENT_READ, REPORT_COMMERCE_READ,
-- REPORT_OPERATIONS_READ, REPORT_EXECUTIVE_READ, REPORT_HISTORY_READ, REPORT_GENERATE. Those are
-- pinned by P13MigrationIntegrationTest.
--
-- This is a forward migration: V24 is already applied in every environment, and migrations are
-- append-only. Idempotent.
-- ---------------------------------------------------------------------------

INSERT INTO role_permission (id, role_id, permission_id, status, created_by, created_on)
SELECT gen_random_uuid(), r.id, p.id, 'ACTIVE', '00000000-0000-0000-0000-000000000001', now()
FROM role r
JOIN permission p ON p.code = 'REPORT_STUDENT_READ'
WHERE r.code = 'STUDENT'
ON CONFLICT (role_id, permission_id) DO NOTHING;
