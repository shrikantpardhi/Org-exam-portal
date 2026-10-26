---
title: Configuration guide
order: 4
---

# Configuration

An organization admin configures the organization from the administration portal.

## Settings

Organization settings hold display and behavioural preferences, such as the time zone used to
render schedules.

## Business rules

Business rules parametrize the platform per organization. For example, MAX_ADMINS_PER_ORG caps
the number of administrators an organization may have (one per organization), and the cap is
enforced by the server when a role is assigned. Rules are resolved at the point of use and never
hard-coded.

## Lookups

Lookups provide enumerations such as statuses and categories, keeping reference data consistent.

## Providers

Provider status reports whether email and payment adapters are configured for the organization.

## Feature flags

Feature flags enable or disable capabilities per organization without redeploying.
