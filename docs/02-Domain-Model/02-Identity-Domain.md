# Domain Model & ERD v1.0

## 02. Identity Domain

## Purpose

The Identity Domain manages organization ownership, users, roles, permissions, login sessions, and access control.

## Core Entities

- Organization
- User
- Role
- Permission
- UserRole
- RolePermission
- LoginSession

## Entity Relationships

- One Organization has many Users
- One User can have many Roles
- One Role can have many Permissions
- One User can have many LoginSessions

## Responsibilities

- Authenticate users securely
- Authorize actions through RBAC
- Maintain user and organization lifecycle
- Track login sessions and security events

## Business Rules

- Every user belongs to one organization
- Roles are assigned to users through mappings
- Permissions are not edited directly by users in Version 1
- Login session records are immutable after creation

## Status

Part of the frozen Version 1.0 architecture baseline.
