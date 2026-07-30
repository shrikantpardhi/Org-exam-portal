# Domain Model & ERD v1.0

## 11. Administration Domain

## Purpose

The Administration Domain manages organization settings, business rules, lookup values, and audit logs.

## Core Entities

- OrganizationSetting
- BusinessRule
- Lookup
- AuditLog

## Entity Relationships

- One Organization has one OrganizationSetting record
- BusinessRule records are global or organization-scoped depending on configuration
- Lookup values are reusable across the platform
- One User can create many AuditLog records

## Responsibilities

- Store configurable organization-wide settings
- Manage operational business rules
- Maintain reusable lookup values
- Record immutable audit trails

## Business Rules

- Configuration should be editable without code changes where possible
- Audit logs are immutable
- Lookup values are shared master data

## Status

Part of the frozen Version 1.0 architecture baseline.
