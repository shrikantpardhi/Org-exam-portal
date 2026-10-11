/**
 * Infrastructure module boundary.
 *
 * <p>Owns BackgroundJob and the non-business infrastructure concerns of the platform: the worker
 * and scheduler foundation, and infrastructure-only endpoints such as
 * {@link com.company.exam.infrastructure.web.SystemInfoController}.
 *
 * <p>Snapshot preparation state does not live here; it lives on {@code student_attempt}. The
 * general-purpose {@code background_job} table is created in its own phase — P0 establishes only
 * the worker boundary and lifecycle ({@code specs/09-P0-Foundation-V1.5.md §57, §136, §140}).
 *
 * <p>See {@code specs/06-Architecture-Freeze-V1.5.md §41}.
 */
package com.company.exam.infrastructure;
