# Database Design v1.0

## 10. Communication Tables

## Purpose

The Communication tables store notifications, email templates, and announcements.

## Tables

### notification

Stores user notifications sent by the platform.

### email_template

Stores reusable email templates.

### announcement

Stores organization-wide announcements and broadcasts.

## Key Relationships

- user → notification (1:N)
- user → announcement (1:N as author)
- email_template is reusable across communication events

## Design Rules

- Notifications are user-specific
- Email templates should be reusable and editable by administrators
- Announcements should be targeted and auditable

## Status

This section is part of the frozen Version 1.0 architecture baseline.
