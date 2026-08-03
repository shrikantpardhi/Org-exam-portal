# API Contract v1.0

## 02. Identity APIs

## Purpose

The Identity APIs manage authentication, users, roles, permissions, login sessions, and organization-level access control.

## Core Endpoints

### Authentication

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/logout`
- `POST /api/v1/auth/refresh-token`
- `POST /api/v1/auth/forgot-password`
- `POST /api/v1/auth/reset-password`

### Users

- `GET /api/v1/users`
- `GET /api/v1/users/{id}`
- `POST /api/v1/users`
- `PUT /api/v1/users/{id}`
- `DELETE /api/v1/users/{id}`

### Roles

- `GET /api/v1/roles`
- `GET /api/v1/roles/{id}`
- `POST /api/v1/roles`
- `PUT /api/v1/roles/{id}`
- `GET /api/v1/roles/{id}/permissions`
- `PUT /api/v1/roles/{id}/permissions`

### Permissions

- `GET /api/v1/permissions`

### Login Sessions

- `GET /api/v1/login-sessions`
- `GET /api/v1/login-sessions/{id}`

## Responsibilities

- Authenticate users securely
- Enforce RBAC across the platform
- Manage user lifecycle and sessions
- Support password reset and account management

## Request and Response Expectations

- All identity APIs use JSON
- UUIDs are used in path parameters
- Protected endpoints require JWT
- Validation errors return a consistent error format

## Business Rules

- Email and mobile must be unique
- Passwords must never be returned in responses
- Permissions are controlled centrally
- Login sessions should be auditable

## Status

This is part of the frozen Version 1.0 architecture baseline.
