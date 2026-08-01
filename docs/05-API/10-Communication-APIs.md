# API Contract v1.0

## 10. Communication APIs

## Purpose

The Communication APIs manage notifications, email templates, and announcements.

## Core Endpoints

- `GET /api/v1/notifications`
- `GET /api/v1/notifications/{id}`
- `POST /api/v1/notifications/{id}/mark-read`
- `POST /api/v1/notifications/mark-all-read`
- `GET /api/v1/email-templates`
- `POST /api/v1/email-templates`
- `GET /api/v1/email-templates/{id}`
- `PUT /api/v1/email-templates/{id}`
- `GET /api/v1/announcements`
- `POST /api/v1/announcements`
- `GET /api/v1/announcements/{id}`

## Responsibilities

- Deliver notifications to users
- Support reusable email templates
- Manage administrative announcements

## Business Rules

- Notifications are user-specific
- Email templates may be reused by different events
- Announcements should be auditable

## Status

This is part of the frozen Version 1.0 architecture baseline.
