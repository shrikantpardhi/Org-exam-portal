# Database Design v1.0

## 09. Commerce Tables

## Purpose

The Commerce tables store access packages, subscription plans, purchases, payment records, access grants, and coupons.

## Tables

### access_package

Stores the entitlement package that defines what the user can access.

### subscription_plan

Stores the commercial plans mapped to an access package.

### purchase

Stores purchase transactions made by users.

### payment_record

Stores payment execution details and references.

### access_grant

Stores the actual entitlement granted to a user.

### coupon

Stores promotional discount codes and eligibility information.

## Key Relationships

- access_package → subscription_plan (1:N)
- subscription_plan → purchase (1:N)
- purchase → payment_record (1:1)
- purchase → access_grant (1:N)
- coupon → purchase (1:N optional)

## Design Rules

- AccessGrant is the source of truth for entitlement
- Purchase records should be immutable after completion
- Payment records should retain gateway references and status
- Coupons must be validated before purchase completion

## Status

This section is part of the frozen Version 1.0 architecture baseline.
