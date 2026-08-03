# Domain Model & ERD v1.0

## 09. Commerce Domain

## Purpose

The Commerce Domain manages access packages, subscription plans, purchases, payment records, access grants, and coupons.

## Core Entities

- AccessPackage
- SubscriptionPlan
- Purchase
- PaymentRecord
- AccessGrant
- Coupon

## Entity Relationships

- One AccessPackage can have many SubscriptionPlans
- One SubscriptionPlan can have many Purchases
- One Purchase has one PaymentRecord
- One Purchase can create one or more AccessGrants
- One AccessPackage can have many AccessGrants
- One Coupon can be applied to many Purchases

## Responsibilities

- Control monetization of assessments
- Track payments and refunds
- Grant access to purchased content
- Support promotions and coupons

## Business Rules

- AccessGrant is the source of truth for entitlement
- Purchase records are immutable after completion
- Coupons must be validated before purchase confirmation
- Payment records are retained for auditability

## Status

Part of the frozen Version 1.0 architecture baseline.
