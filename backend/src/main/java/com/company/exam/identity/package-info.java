/**
 * Identity module boundary.
 *
 * <p>Owns Organization, User, Role, Permission, UserRole, RolePermission, and LoginSession, and is
 * responsible for authentication, authorization, user lifecycle, role assignment, and session
 * lifecycle.
 *
 * <p>P0 establishes this boundary only; the module is implemented in P1. It does not own
 * assessment business rules.
 *
 * <p>See {@code specs/06-Architecture-Freeze-V1.5.md §31}.
 */
package com.company.exam.identity;
