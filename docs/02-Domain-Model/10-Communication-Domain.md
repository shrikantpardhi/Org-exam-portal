# Domain Model & ERD v1.0

## 10. Communication Domain

## Purpose

The Communication Domain manages notifications, email templates, and announcements.

## Core Entities

- Notification
- EmailTemplate
- Announcement

## Entity Relationships

- One User has many Notifications
- One EmailTemplate may be reused by many communication events
- One Announcement is created by one User and targeted to many recipients or groups

## Responsibilities

- Deliver platform notifications
- Support reusable email communication
- Publish administrative announcements

## Business Rules

- Notifications are user-specific
- Email templates are reusable and configurable
- Announcements may be targeted to user groups

## Status

Part of the frozen Version 1.0 architecture baseline.
