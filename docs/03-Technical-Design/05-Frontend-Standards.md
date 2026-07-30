# Technical Design v1.0

## 05. Frontend Standards

## Purpose

This document defines frontend implementation standards for the platform.

## Frontend Stack

- Next.js
- TypeScript
- Tailwind CSS
- shadcn/ui
- TanStack Query
- React Hook Form
- Zod
- Axios

## UI Principles

- Build responsive layouts first
- Keep screens role-aware
- Use reusable components
- Prefer server-friendly data loading where appropriate
- Avoid unnecessary client-side complexity

## State and Data Handling

- Use TanStack Query for server state
- Use React Hook Form for forms
- Use Zod for validation
- Use Axios or a shared API client for backend communication

## Design Standards

- Role-based navigation
- Consistent page layouts
- Shared component library
- Strong loading, empty, and error states
- Accessible UI controls

## Performance Standards

- Keep page bundles small
- Avoid over-fetching
- Cache where appropriate
- Use lazy loading for large views where needed

## Status

This is part of the frozen Version 1.0 architecture baseline.
