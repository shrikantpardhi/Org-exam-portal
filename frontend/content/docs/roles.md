---
title: Roles
order: 2
---

# Roles

The platform has seven roles. Permission is granted through access grants, and every surface is
permission-gated.

- **SUPER_ADMIN** — manages organizations, providers, and platform-wide settings. Signs in through the system administrator portal.
- **ADMIN** — owns a single organization: its users, content, schedules, and configuration. An organization has at most one admin. Signs in through the organization portal.
- **STAFF** — authors and manages questions, blueprints, and assessments. Signs in through the organization portal.
- **REVIEWER** — reviews content before it is used. Signs in through the organization portal.
- **SUPPORT** — helps users within an organization. Signs in through the organization portal.
- **FINANCE** — manages commerce, purchases, and revenue. Signs in through the organization portal.
- **STUDENT** — attempts exams and views results. Signs in through the student portal.

A user may hold more than one role. The effective permissions are the union of the permissions
granted to the user's roles, and authorization is always decided from those grants on the server —
never from hard-coded role checks in the interface.
