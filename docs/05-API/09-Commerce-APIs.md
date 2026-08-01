# API Contract v1.0

## 09. Commerce APIs

## Purpose

The Commerce APIs manage access packages, subscription plans, purchases, payments, coupons, and access grants.

## Core Endpoints

- `GET /api/v1/access-packages`
- `POST /api/v1/access-packages`
- `GET /api/v1/access-packages/{id}`
- `GET /api/v1/subscription-plans`
- `POST /api/v1/subscription-plans`
- `GET /api/v1/subscription-plans/{id}`
- `POST /api/v1/purchases`
- `GET /api/v1/purchases/{id}`
- `GET /api/v1/payment-records/{id}`
- `POST /api/v1/coupons`
- `GET /api/v1/coupons/{id}`
- `GET /api/v1/access-grants`
- `POST /api/v1/access-grants`

## Responsibilities

- Support monetization of assessments
- Process purchases and payment records
- Grant access based on purchases or manual actions
- Support coupons and promotional campaigns

## Business Rules

- AccessGrant is the source of truth for entitlement
- Purchases should map to a subscription plan
- Payment records should retain transaction references
- Coupons should be validated before use

## Status

This is part of the frozen Version 1.0 architecture baseline.
