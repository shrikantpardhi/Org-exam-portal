/**
 * Administration module boundary.
 *
 * <p>Owns OrganizationSetting, BusinessRule, Lookup, and AuditLog. Responsible for system
 * configuration, operational control, auditing, master configuration, global search, imports,
 * bulk operations, and support operations.
 *
 * <p>Audit insertion is transactional with the business mutation. P0 establishes this boundary
 * only; the module is implemented in P11.
 *
 * <p>See {@code specs/06-Architecture-Freeze-V1.5.md §40}.
 */
package com.company.exam.administration;
