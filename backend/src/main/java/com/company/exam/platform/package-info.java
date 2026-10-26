/**
 * Platform administration (SUPER_ADMIN) module.
 *
 * <p>Owns the installation-owner surface: business-organization lifecycle, the one-ADMIN-per-org
 * cap, platform status/system information, and provider configuration. Every endpoint is
 * SUPER_ADMIN-only and authorized by {@code PLATFORM_*} permissions (ADR-0032, ADR-0033).
 *
 * <p>See {@code specs/05-API-Contract-V1.5.md §72} and {@code specs/20-P11-Administration-V1.5.md
 * §18}.
 */
package com.company.exam.platform;
