# Database Design v1.0

## 02. Identity Tables

## Purpose

The Identity tables store the organization, users, roles, permissions, login sessions, and role mappings used for authentication and authorization.

## Tables

### organization

Stores the organization using the platform.

### user

Stores all users belonging to the organization.

### role

Stores reusable roles such as admin, reviewer, staff, support, and student.

### permission

Stores granular permissions used in RBAC.

### user_role

Maps users to roles.

### role_permission

Maps roles to permissions.

### login_session

Stores login session history and security metadata.

## Key Relationships

- organization → user (1:N)
- user ↔ role (M:N via user_role)
- role ↔ permission (M:N via role_permission)
- user → login_session (1:N)

## Design Rules

- Email and mobile should be unique where applicable.
- Passwords are stored only as hashes.
- Sessions should record timestamps and security metadata.
- Permissions should be controlled centrally rather than edited ad hoc.

## Status

This section is part of the frozen Version 1.0 architecture baseline.
