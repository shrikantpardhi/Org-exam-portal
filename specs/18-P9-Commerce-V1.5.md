# Smart Competitive Examination Platform
## P9 — Commerce & Access Management Implementation Specification

**Version:** 1.5
**Status:** IMPLEMENTATION-READY
**Phase:** P9 — Commerce & Access Management
**Depends on:** P0 Foundation, P1 Identity and Security, P2 Academic Structure, P3 Content and Question Bank, P4 Blueprint and Assessment Management, P5 Assessment Delivery and Runtime Examination Engine, P6 Evaluation and Results, P7 Scale Validation and Live Pilot, P8 Full Analytics and Insights
**Architecture:** Modular Monolith
**Organization Scope:** Single Organization per Deployment
**Backend:** Java 25 + Spring Boot 4
**Frontend:** Next.js + TypeScript
**Database:** PostgreSQL
**Cache:** Redis
**Migration:** Flyway
**API:** REST + JSON, `/api/v1`
**Audience:** Backend Engineers, Frontend Engineers, QA, DevOps, Security, Finance Operations, Architects

---

## 1. Purpose

This document converts the Commerce and Access Management domain into an implementation-ready engineering specification.

P9 is intentionally introduced after the examination engine has been validated through the pilot-first roadmap. The first pilot uses controlled AccessGrant provisioning. Commercial checkout, payment-provider integration, refunds, subscription plans, bundles, and coupon workflows are implemented in this phase.

Commerce is responsible for:

```text
AccessPackage
AccessPackageCollection
SubscriptionPlan
Purchase
PaymentRecord
AccessGrant
Coupon
```

The domain does not own authentication, assessment, delivery, evaluation, analytics, or communication.

---

## 2. Source-of-truth hierarchy

Read these documents before P9 implementation:

- Master PRD
- Domain Model
- Technical Design Document
- Database Standards and Schema
- API Contract
- Architecture Freeze
- Development Roadmap
- ExamEngine and CDUI Contract
- P0 Foundation Implementation Specification
- P1 Identity and Security Implementation Specification
- P2 Academic Structure Implementation Specification
- P3 Content and Question Bank Implementation Specification
- P4 Blueprint and Assessment Management Implementation Specification
- P5 Assessment Delivery and Runtime Examination Engine Implementation Specification
- P6 Evaluation and Results Implementation Specification
- P7 Scale Validation and Live Pilot Implementation Specification
- P8 Full Analytics and Insights Implementation Specification
- This document

The earlier documents already freeze the commerce schema, the authorization boundary, and the payment provider isolation principle. This document does not modify those decisions.

---

## 3. Core commerce separation

The following concepts remain separate.

```text
SubscriptionPlan  -> what is being sold and its pricing
AccessPackage     -> what the customer receives
AccessGrant      -> what the user is currently authorized to access
Purchase         -> the commercial event
PaymentRecord    -> the payment transaction
```

These are not merged.

### 3.1 Purchase is not access

A successful purchase does not itself become the authorization record.

Intended flow.

```text
SubscriptionPlan
      |
      v
Purchase
      |
      v
PaymentRecord
      |
      v
AccessGrant
      |
      v
Assessment authorization
```

Assessment delivery asks the authorization boundary whether access is permitted. It does not query payment records to infer entitlement.

### 3.2 AccessGrant is the authorization source of truth

For paid content.

```text
Can user U access package P?
        |
        v
Evaluate AccessGrant
```

A valid grant is evaluated using status, start_date, end_date, and package entitlement resolution.

### 3.3 Commerce flow

```text
Assessment
    |
    v
AssessmentCollection
    |
    v
AccessPackage
    |
    v
SubscriptionPlan
    |
    v
Purchase
    |
    v
PaymentRecord
    |
    v
AccessGrant
```

---

## 4. P9 ownership

P9 owns:

```text
AccessPackage
AccessPackageCollection
SubscriptionPlan
Purchase
PaymentRecord
AccessGrant
Coupon
Pricing calculation
Coupon application
Payment initiation orchestration
Payment callback normalization
Refund orchestration
Entitlement creation and update
Entitlement expiry evaluation
Commerce audit events
Commerce read models derived from existing transaction tables
```

Other modules own:

- Assessment, AssessmentVersion, AssessmentSchedule, ExamEngine, Delivery rules, Attempt eligibility
- AssessmentSession, StudentAttempt, StudentResponse, snapshot preparation
- AnswerKey, Evaluation, Result, Scorecard
- User, Role, Permission, LoginSession, JWT authentication, RBAC
- OrganizationSetting, BusinessRule, Lookup, AuditLog

Administration presents operational access to Commerce functions. It does not bypass Commerce services.

---

## 5. P9 non-goals

P9 does not implement:

```text
Advanced marketing campaign engine
Affiliate or referral domain
Tax-invoice domain beyond the frozen schema
Partial-refund ledger
Chargeback case-management domain
Multiple payment providers simultaneously unless explicitly introduced
Recurring billing scheduler beyond the frozen SubscriptionPlan concept
Complex promotion rules
Usage-limited coupons
Per-user coupon quotas
Percentage discount unless explicitly approved
Complex tax calculation
Multi-organization SaaS billing
Payment-provider-specific persistent tables not frozen by the database contract
Wallet, credit, gift cards
Marketplace
```

---

## 6. Aggregate boundaries

Recommended commerce aggregate roots.

```text
AccessPackage
SubscriptionPlan
Purchase
AccessGrant
Coupon
```

PaymentRecord is owned through the purchase and payment transaction boundary and is not independently mutated by generic controllers.

A single commercial operation may coordinate Purchase, PaymentRecord, and AccessGrant with external provider interaction and idempotency controls.

---

## 7. Frozen database schema

### 7.1 Table: access_package

```sql
access_package
--------------
id UUID PK
code VARCHAR(50)
name VARCHAR(200)
description TEXT
status
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Unique: `UNIQUE(code)`.

### 7.2 Table: access_package_collection

```sql
access_package_collection
-------------------------
id UUID PK
access_package_id UUID FK
assessment_collection_id UUID FK
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
status
```

Unique: `UNIQUE(access_package_id, assessment_collection_id)`.

Indexes.

```text
idx_access_package_collection_access_package_id
idx_access_package_collection_assessment_collection_id
```

### 7.3 Table: subscription_plan

```sql
subscription_plan
-----------------
id UUID PK
access_package_id UUID FK
code VARCHAR(50)
name VARCHAR(150)
duration_days INTEGER
price NUMERIC(10,2)
status
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Unique: `UNIQUE(code)`.

Index: `idx_subscription_plan_access_package_id`.

### 7.4 Table: purchase

```sql
purchase
--------
id UUID PK
user_id UUID FK
subscription_plan_id UUID FK
coupon_id UUID FK NULL
purchase_date TIMESTAMPTZ
amount NUMERIC(10,2)
payment_status
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Indexes.

```text
idx_purchase_user_id
idx_purchase_subscription_plan_id
idx_purchase_coupon_id
idx_purchase_purchase_date
idx_purchase_payment_status
```

### 7.5 Table: payment_record

```sql
payment_record
--------------
id UUID PK
purchase_id UUID FK
payment_reference VARCHAR(255)
payment_method VARCHAR(100)
payment_status
payment_date TIMESTAMPTZ
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Unique: `UNIQUE(purchase_id)`.

A uniqueness constraint on `payment_reference` is applied when the chosen provider guarantees globally unique references.

### 7.6 Table: access_grant

```sql
access_grant
------------
id UUID PK
user_id UUID FK
access_package_id UUID FK
start_date TIMESTAMPTZ
end_date TIMESTAMPTZ
status
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Indexes.

```text
idx_access_grant_user_id
idx_access_grant_access_package_id
idx_access_grant_start_date
idx_access_grant_end_date
idx_access_grant_status
```

### 7.7 Table: coupon

```sql
coupon
------
id UUID PK
code VARCHAR(50)
name VARCHAR(150)
discount_value NUMERIC(10,2)
valid_from TIMESTAMPTZ
valid_to TIMESTAMPTZ
status
created_by UUID
created_on TIMESTAMPTZ
updated_by UUID
updated_on TIMESTAMPTZ
```

Unique: `UNIQUE(code)`.

---

## 8. AccessPackage

Purpose: represents the actual entitlement bundle delivered to a user.

Example.

```text
SSC Mock Test Package
    |
    +-- SSC Collection A
    +-- SSC Collection B
    +-- SSC Collection C
```

Rules:

```text
code is unique
status follows the standard lifecycle
archived packages remain historically referenceable
package metadata is not itself an authorization grant
a package can be sold through one or more subscription plans
a package can be granted manually without a purchase
a package unlocks one or more assessment collections through the relational junction table
package-to-collection mapping is not stored as JSONB
```

Lifecycle.

```text
ACTIVE
  |
  +-- INACTIVE
  |
  +-- ARCHIVED
```

The application prevents an inactive or archived package from being newly purchased unless a formal business rule explicitly permits it.

Existing historical purchases and grants remain queryable.

---

## 9. AccessPackageCollection

Purpose: defines which assessment collections are unlocked by an access package.

Frozen schema and uniqueness as above.

### 9.1 Entitlement resolution

```text
User
  |
  v
AccessGrant
  |
  v
AccessPackage
  |
  v
AccessPackageCollection
  |
  v
AssessmentCollection
  |
  v
Assessment
```

The resolver never infers collection entitlement from a Purchase alone.

### 9.2 Versioning rule

AssessmentCollection references Assessment, not an assessment version.

The current-version semantics of a package's collection entitlement follow the assessment and collection contract and are not duplicated inside Commerce.

---

## 10. SubscriptionPlan

Purpose: defines the commercial offer.

Examples.

```text
SSC Package, 30 Days
SSC Package, 90 Days
SSC Package, 180 Days
```

Rules.

```text
access_package_id required
code unique
duration_days positive
price NUMERIC(10,2)
```

The customer never submits the final payable amount.

The server derives the amount from SubscriptionPlan.price, validated discount, and any explicitly approved future adjustment.

No client-supplied total is authoritative.

### 10.1 Publication and activation

The frozen schema uses the standard status field.

```text
ACTIVE
INACTIVE
ARCHIVED
```

This is the authoritative lifecycle unless a later contract adds a separate publication state.

---

## 11. Purchase

Purpose: a user's commercial purchase event.

Relationships.

```text
User 1:N Purchase
SubscriptionPlan 1:N Purchase
Coupon 0:N Purchase
Purchase 1:1 PaymentRecord
```

### 11.1 Amount invariant

At purchase creation:

```text
Client subtotal  -> ignored
Client total     -> ignored
Client discount  -> ignored

Server:
  Plan lookup
  Price calculation
  Coupon validation
  Final purchase.amount
```

The stored amount is the server-calculated commercial amount for that purchase.

No floating-point arithmetic is permitted.

Use BigDecimal and a fixed currency and rounding policy.

### 11.2 Purchase creation request

```http
POST /api/v1/purchases
```

Request.

```json
{
  "subscriptionPlanId": "<uuid>",
  "couponCode": "WELCOME10"
}
```

Server flow.

```text
Authenticate user
      |
      v
Load active SubscriptionPlan
      |
      v
Load AccessPackage
      |
      v
Validate package
      |
      v
Validate coupon if supplied
      |
      v
Calculate final amount
      |
      v
Create Purchase
      |
      v
Create PaymentRecord only at the payment boundary
```

The exact timing of PaymentRecord creation relative to purchase creation is consistent and idempotent.

### 11.3 Purchase ownership

Students see their own purchases.

Finance and admin users access broader purchase lists according to permissions.

A student does not read another student's purchase by changing a UUID.

Object-level authorization is required in addition to endpoint-level RBAC.

---

## 12. PaymentRecord

Purpose: stores payment-specific information separately from the Purchase business record.

Frozen statuses.

```text
PENDING
SUCCESS
FAILED
REFUNDED
```

Provider-specific lifecycle values are not added to the core database.

Provider statuses are normalized at the adapter boundary.

Example.

```text
Provider: "captured" -> SUCCESS
Provider: "failed"   -> FAILED
```

The exact provider mapping is documented per adapter.

---

## 13. Payment provider boundary

The exact payment provider is not frozen.

Provider-specific code is isolated behind an application contract.

Suggested interface.

```java
public interface PaymentGateway {

    PaymentProviderId provider();

    PaymentInitiationResult initiate(
        PaymentInitiationCommand command
    );

    PaymentCallbackResult verifyAndNormalize(
        PaymentCallbackCommand command
    );

    RefundResult refund(
        RefundCommand command
    );
}
```

Provider implementations live below the Commerce application boundary.

The rest of the platform depends on PaymentGateway, not on a concrete gateway SDK.

### 13.1 Provider data rule

Provider-specific payloads do not leak into Purchase DTOs, PaymentRecord entity, AccessGrant entity, or student API models.

Provider secrets are never persisted in the frozen domain tables.

---

## 14. AccessGrant

Purpose: the authoritative authorization record for paid access.

### 14.1 Validity

```text
status == ACTIVE
AND start_date <= now
AND end_date >= now
```

Boundary inclusivity and null end_date semantics are frozen as follows.

```text
start_date inclusive
end_date inclusive
end_date may be NULL when the grant does not expire
server time is authoritative
```

### 14.2 Authorization query

```sql
SELECT 1
FROM access_grant ag
WHERE ag.user_id = :userId
  AND ag.access_package_id = :packageId
  AND ag.status = 'ACTIVE'
  AND ag.start_date <= :serverNow
  AND (ag.end_date IS NULL OR ag.end_date >= :serverNow)
LIMIT 1;
```

The real implementation also resolves whether the target assessment belongs to the package through the package-to-collection mapping.

### 14.3 Multiple grants

Multiple grants for the same user and package may exist historically.

The service does not assume one user equals one grant.

For active authorization, the result is ALLOW when at least one valid grant exists.

### 14.4 Manual grants

```http
POST /api/v1/access-grants
```

Example.

```json
{
  "userId": "<uuid>",
  "accessPackageId": "<uuid>",
  "startDate": "2026-10-01T00:00:00Z",
  "endDate": "2026-12-30T23:59:59Z"
}
```

Allowed workflows: STAFF, ADMIN, SUPPORT, subject to permission checks.

Every manual grant is audited.

---

## 15. Coupon

Purpose: represents a discount mechanism.

Frozen schema uses a single `discount_value NUMERIC(10,2)`.

### 15.1 Coupon semantics

The frozen implementation treats `discount_value` as a fixed amount discount in the platform currency.

Discount application rules.

```text
Discount is subtracted from the subscription plan price
Discount cannot exceed the plan price
Result is never negative
Rounding uses HALF_UP at 2 decimals
```

The coupon code is normalized to upper case for lookup.

The valid_from boundary is inclusive. The valid_to boundary is inclusive.

An inactive or archived coupon is treated as invalid.

A coupon applies only to the subscription plan supplied in the validation request.

### 15.2 Coupon calculation

The pricing calculator returns.

```text
listPrice
discount
finalAmount
currency
calculationVersion
```

Only fields supported by the frozen schema are persisted.

The calculation version itself is not added as a database field.

---

## 16. Freemium and free assessments

Students access free assessments without a paid purchase when the assessment is configured as free.

Conceptual path.

```text
Request
   |
   v
Authentication
   |
   v
Assessment free?
   |
   +-- Yes -> Continue
   |
   +-- No  -> AccessGrant check
```

Analytics may still be generated for free attempts.

Free access handling does not bypass normal authentication, assessment publication, schedule eligibility, delivery policy, attempt creation, or snapshot readiness.

Only the paid-entitlement check changes.

No new persistent free_access table is required.

---

## 17. Bundles

A bundle is represented by AssessmentCollection to AccessPackage to SubscriptionPlan.

Example.

```text
SSC Mega Bundle
    |
    +-- Collection A
    +-- Collection B
    +-- Collection C
    +-- Collection D
```

Mixed content is permitted. The package grants entitlement at the package and collection level.

The assessment authorization service still evaluates the final assessment access rule.

Bundle validation checks that package exists, collection exists, both are valid, no duplicate mapping exists, and collection is not archived where business rules prohibit it.

---

## 18. Commerce state model

The schema does not have a separate Purchase status. Use the existing payment_status fields.

### 18.1 Purchase and payment state

```text
PENDING
  |
  +-- SUCCESS
  |
  +-- FAILED

SUCCESS
  |
  +-- REFUNDED
```

The exact legal transitions are enforced in one state-transition service.

Invalid transitions return 409 CONFLICT with INVALID_STATE.

### 18.2 AccessGrant state

```text
ACTIVE
INACTIVE
ARCHIVED
```

A payment failure never creates an active paid AccessGrant.

A successful payment may create or update an AccessGrant after all validations succeed.

A refund's effect on an existing AccessGrant follows the approved refund policy.

---

## 19. AccessGrant creation policy

Purchase-to-grant date and merge policy.

```text
grant start date = purchase_date
grant end date = purchase_date + subscription_plan.duration_days

If an active grant for the same user and package already exists,
extend end_date = max(existing_end_date, purchase_date) + duration_days
```

The policy is applied server-side and audited.

Manual grants use the operator-supplied dates.

---

## 20. Refund policy

The frozen schema supports full refunds only.

Multiple refund events are not independently persisted by the frozen model.

Refund flow.

```text
Purchase
  |
  v
PaymentRecord
  |
  v
Provider refund
  |
  v
Normalize refund result
  |
  v
Payment status REFUNDED
  |
  v
Purchase payment_status REFUNDED
  |
  v
Access entitlement policy
```

Approved access revocation policy.

```text
On refund, associated AccessGrant is moved to INACTIVE
with end_date set to the refund time.
```

Provider refund idempotency is enforced when the provider supports it. Otherwise, the application uses an idempotency key to prevent duplicate provider calls.

---

## 21. Payment callback idempotency

Callback handling is transactionally safe.

Preferred existing-data idempotency path.

```text
Provider payment reference
        |
        v
payment_record.payment_reference
```

with uniqueness applied when the provider guarantees a globally unique payment reference.

The frozen schema has no dedicated callback-event table.

If the selected provider cannot be made safe with the frozen schema, that becomes a formal contract change and is resolved before implementation.

---

## 22. Payment callback security

Callback processing is provider-facing and is treated as hostile input.

Required.

```text
Authenticity verification
Reference validation
Amount validation
Purchase correlation
Status normalization
Replay protection
Idempotency
Audit
```

The callback does not accept a user id or access package from the provider payload as an authoritative entitlement source.

The authoritative purchase linkage.

```text
payment_reference
  |
  v
PaymentRecord
  |
  v
Purchase
  |
  v
SubscriptionPlan
  |
  v
AccessPackage
```

### 22.1 Amount verification

A successful callback does not grant access solely because the provider says paid.

Verify provider payment reference, expected purchase, expected amount, expected currency where supplied, and authentic callback.

If these cannot be verified, do not grant access.

---

## 23. Callback transaction

For a successful callback.

```text
BEGIN
  Lock and load Purchase
  Lock and load PaymentRecord
  Validate provider reference
  Validate expected amount and state
  Normalize provider status
  Update PaymentRecord
  Update Purchase.payment_status
  Create or update AccessGrant
  Write audit
COMMIT
```

If any invariant fails, the transaction rolls back and no AccessGrant is activated.

---

## 24. Payment failure

Provider failure.

```text
Provider FAILED
    |
    v
PaymentRecord FAILED
    |
    v
Purchase.payment_status FAILED
    |
    v
No paid AccessGrant
```

Entitlement is not created or extended on failure.

---

## 25. Successful payment to AccessGrant

```text
SUCCESS payment
      |
      v
Purchase SUCCESS
      |
      v
Resolve AccessPackage
      |
      v
Calculate grant dates
      |
      v
Create or update AccessGrant
      |
      v
Assessment authorization becomes ALLOW
```

---

## 26. Refund flow

```http
POST /api/v1/purchases/{purchaseId}/refund
```

Permission: FINANCE or ADMIN.

Expected business path.

```text
Purchase
  |
  v
PaymentRecord
  |
  v
Provider refund
  |
  v
Normalize refund result
  |
  v
Payment status REFUNDED
  |
  v
Purchase payment_status REFUNDED
  |
  v
Access entitlement policy
```

Refund idempotency uses purchase state lock, provider idempotency key where supported, and an application idempotency key.

---

## 27. Commerce module structure

Recommended backend structure.

```text
com.company.exam.commerce
  api
    AccessPackageController
    SubscriptionPlanController
    PurchaseController
    PaymentCallbackController
    AccessGrantController
    CouponController
  application
    AccessPackageService
    SubscriptionPlanService
    PurchaseService
    PaymentService
    AccessGrantService
    CouponService
    EntitlementService
    CommerceReconciliationService
  domain
    AccessPackage
    AccessPackageCollection
    SubscriptionPlan
    Purchase
    PaymentRecord
    AccessGrant
    Coupon
  repository
    AccessPackageRepository
    AccessPackageCollectionRepository
    SubscriptionPlanRepository
    PurchaseRepository
    PaymentRecordRepository
    AccessGrantRepository
    CouponRepository
  payment
    PaymentGateway
    PaymentProviderMapper
    provider/
  pricing
    PricingCalculator
    CouponDiscountPolicy
  authorization
    CommerceEntitlementResolver
  audit
    CommerceAuditService
```

Cross-module repository access is prohibited.

---

## 28. Application contracts

### 28.1 Entitlement resolver

```java
public interface EntitlementResolver {

    AccessDecision checkAssessmentAccess(
        UUID userId,
        UUID assessmentId,
        Instant serverNow
    );
}
```

The resolver owns the path Assessment to Collection to Package to Grant, or the approved free-assessment path.

### 28.2 Purchase service

```java
public interface PurchaseService {

    PurchaseView createPurchase(
        UUID userId,
        CreatePurchaseCommand command,
        IdempotencyContext idempotency
    );

    PurchaseView getPurchase(UUID requestingUserId, UUID purchaseId);

    Page<PurchaseView> listPurchases(PurchaseQuery query);

    PaymentInitiationView initiatePayment(
        UUID userId,
        UUID purchaseId,
        IdempotencyContext idempotency
    );
}
```

### 28.3 Payment service

```java
public interface PaymentService {

    PaymentInitiationView initiate(UUID purchaseId);

    PaymentCallbackOutcome handleCallback(
        PaymentCallbackCommand command
    );

    PaymentView getPayment(UUID requestingUserId, UUID purchaseId);

    RefundOutcome refund(
        UUID purchaseId,
        RefundCommand command,
        IdempotencyContext idempotency
    );
}
```

### 28.4 AccessGrant service

```java
public interface AccessGrantService {

    AccessGrantView grant(GrantAccessCommand command);

    AccessDecision check(
        UUID userId,
        UUID accessPackageId,
        Instant now
    );

    Page<AccessGrantView> list(AccessGrantQuery query);

    AccessGrantView get(UUID id);

    AccessGrantView revoke(
        UUID grantId,
        RevokeAccessCommand command
    );
}
```

---

## 29. API contract

### 29.1 Access package

```http
GET    /api/v1/access-packages
GET    /api/v1/access-packages/{id}
POST   /api/v1/access-packages
PUT    /api/v1/access-packages/{id}
DELETE /api/v1/access-packages/{id}
```

### 29.2 Package to collection mapping

```http
GET    /api/v1/access-packages/{packageId}/collections
POST   /api/v1/access-packages/{packageId}/collections
DELETE /api/v1/access-packages/{packageId}/collections/{collectionId}
```

Add collection request.

```json
{
  "collectionId": "<uuid>"
}
```

### 29.3 Subscription plan

```http
GET    /api/v1/subscription-plans
GET    /api/v1/subscription-plans/{id}
POST   /api/v1/subscription-plans
PUT    /api/v1/subscription-plans/{id}
DELETE /api/v1/subscription-plans/{id}
```

Filters: accessPackageId, status.

Create request.

```json
{
  "accessPackageId": "<uuid>",
  "code": "SSC-90-DAYS",
  "name": "SSC 90 Days",
  "durationDays": 90,
  "price": 999.00
}
```

### 29.4 Purchase

```http
POST /api/v1/purchases
GET  /api/v1/purchases
GET  /api/v1/purchases/{id}
```

### 29.5 Payment

```http
POST /api/v1/purchases/{purchaseId}/payment
GET  /api/v1/purchases/{purchaseId}/payment
POST /api/v1/payments/callback
```

### 29.6 Refund

```http
POST /api/v1/purchases/{purchaseId}/refund
```

### 29.7 Access grant

```http
GET  /api/v1/access-grants
GET  /api/v1/access-grants/{id}
POST /api/v1/access-grants
```

Filters: userId, accessPackageId, status, active.

### 29.8 Coupon

```http
GET    /api/v1/coupons
GET    /api/v1/coupons/{id}
POST   /api/v1/coupons
PUT    /api/v1/coupons/{id}
DELETE /api/v1/coupons/{id}
POST   /api/v1/coupons/validate
```

Validate request.

```json
{
  "code": "WELCOME10",
  "subscriptionPlanId": "<uuid>"
}
```

---

## 30. API authorization matrix

| Capability | Student | Staff | Support | Finance | Admin |
|---|---|---|---|---|---|
| Browse active packages | Yes | Yes | Yes | Yes | Yes |
| View own purchase | Yes | No | Support scope | Finance scope | Yes |
| List all purchases | No | No | Limited | Yes | Yes |
| Create purchase | Yes | As authorized | As authorized | Yes if required | Yes |
| Initiate own payment | Yes | No | No | Yes if operationally needed | Yes |
| Payment callback | Provider only | No | No | No | No |
| Refund purchase | No | No | No | Yes | Yes |
| View access grants | Own entitlement | Permitted scope | Permitted scope | Permitted scope | Yes |
| Manual grant | No | Yes | Yes | According to policy | Yes |
| Revoke grant | No | According to permission | According to permission | According to permission | Yes |
| Manage coupons | No | No | No | Yes | Yes |
| Manage subscription plans | No | According to permission | No | Yes | Yes |
| Manage packages | No | According to permission | No | According to permission | Yes |
| Package collection mapping | No | According to permission | No | According to permission | Yes |

Role permissions remain backend-controlled. Role names are not the authorization boundary by themselves.

---

## 31. Purchase creation detailed flow

```text
Client
  |
  v
POST /purchases
  |
  v
Authentication
  |
  v
Authorization: create purchase
  |
  v
Load SubscriptionPlan
  |
  v
Validate ACTIVE
  |
  v
Load AccessPackage
  |
  v
Validate package
  |
  v
Optional Coupon lookup
  |
  v
Coupon policy
  |
  v
Pricing calculation
  |
  v
Create Purchase
  |
  v
Return purchase summary
```

The operation is safe under client retries.

Retry example.

```text
Request A
  |
  v
Purchase created

Request A retry
  |
  v
Same idempotency key
  |
  v
Return existing result
```

Duplicate Purchase records are not created.

---

## 32. Pricing calculator

Trusted inputs.

```text
SubscriptionPlan.price
Coupon record
Approved coupon policy
Approved currency and rounding rules
```

Untrusted inputs.

```text
Client subtotal
Client price
Client discount amount
Client final amount
```

Calculator contract.

```java
public interface PricingCalculator {

    PricingResult calculate(
        SubscriptionPlanSnapshot plan,
        Optional<CouponSnapshot> coupon
    );
}
```

Result.

```text
listPrice
discount
finalAmount
currency
calculationVersion
```

Persist only fields supported by the frozen schema.

---

## 33. Coupon validation flow

```text
POST /coupons/validate
        |
        v
Authenticate
        |
        v
Load code
        |
        v
Normalize code to upper case
        |
        v
Check ACTIVE
        |
        v
Check valid_from
        |
        v
Check valid_to
        |
        v
Load SubscriptionPlan
        |
        v
Apply approved discount semantics
        |
        v
Return result
```

The same pricing rules are re-run on Purchase creation.

A previous browser-side coupon-validation response is not trusted.

---

## 34. Coupon race conditions

Coupon usage limits are not part of the frozen schema.

Therefore no usage counter, hidden JSONB quota, or per-user counter is created.

If future requirements add usage limits, a separate schema and API amendment is required.

---

## 35. Payment initiation flow

```text
Student
   |
   v
POST /purchases/{purchaseId}/payment
   |
   v
Ownership check
   |
   v
Purchase state validation
   |
   v
PaymentRecord existence check
   |
   v
Idempotent gateway initiation
   |
   v
Normalize provider response
   |
   v
Persist payment reference and status
   |
   v
Return provider initiation details required by UI
```

Provider-specific secrets stay server-side.

The frontend receives only the minimal initiation payload required by the selected provider.

---

## 36. Payment completion UX

The frontend treats payment initiated as different from payment successful.

Only backend-confirmed payment state unlocks paid content.

UI authorization is not granted merely from the browser callback or redirect.

---

## 37. Payment status polling

```http
GET /api/v1/purchases/{purchaseId}/payment
```

Used for provider-confirmation recovery where applicable.

The UI shows PAYMENT_PENDING, PAYMENT_SUCCESS, PAYMENT_FAILED, or PAYMENT_REFUNDED mapped from the frozen status vocabulary.

Polling has bounded retry behavior. Tight polling loops are avoided.

---

## 38. Webhook versus browser redirect

If the selected provider uses both browser redirect and server callback, the browser redirect is informational only.

The authoritative state is.

```text
Provider callback
  |
  v
Backend verification
  |
  v
PaymentRecord
  |
  v
Purchase
  |
  v
AccessGrant
```

---

## 39. Commerce query APIs

List APIs support the shared platform conventions.

```text
Pagination
Sort
Filter
Search where approved
```

Unbounded queries returning all purchases or grants are not implemented.

---

## 40. AccessGrant list filters

Frozen filters.

```text
userId
accessPackageId
status
active
```

`active` is a query semantic, not a stored field.

It resolves against status, start_date, end_date, and serverNow.

---

## 41. Finance views

Finance users need focused access to purchases, payments, refunds, coupons, subscription plans, and commerce reports.

They do not automatically receive question publication, assessment publication, or answer-key publication.

Commerce authorization is permission-specific.

---

## 42. Support views

Support may need user lookup, purchase lookup, access lookup, attempt lookup, and session information.

Support may provision manual grants when explicitly authorized.

Support does not receive unrestricted refund rights merely because it can inspect purchases.

---

## 43. Audit requirements

Audit important Commerce actions.

```text
AccessPackage create, update, archive
SubscriptionPlan create, update, archive
Package-collection mapping change
Purchase creation
Payment initiation
Payment callback state transition
Payment failure
Payment success
Refund
Manual access grant
Access revoke
Coupon create, update, archive
Coupon validation where policy requires audit
Commerce rule changes
```

Audit records actor, action, target, timestamp, request correlation id, and outcome.

Sensitive payment secrets are never logged.

Audit insertion participates in the business transaction for critical state changes.

---

## 44. Error model

Use the global error format.

```json
{
  "success": false,
  "message": "Validation failed",
  "errors": []
}
```

Commerce-specific error categories remain within the platform error set.

Examples.

```text
403 AUTHORIZATION_ERROR
404 NOT_FOUND
409 CONFLICT
409 INVALID_STATE
422 VALIDATION_ERROR
```

---

## 45. Commerce error cases

### 45.1 Inactive plan

`POST /purchases` with inactive plan returns `409 INVALID_STATE`.

### 45.2 Invalid coupon

`POST /coupons/validate` may return `422 VALIDATION_ERROR` or an approved business error code.

### 45.3 Expired grant

Authorization returns `403 AUTHORIZATION_ERROR` or a domain-equivalent access denial according to the global access-control contract.

### 45.4 Duplicate callback

Returns a successful idempotent response representing the already-applied state rather than creating a second business event.

---

## 46. DTO rules

Entities are not exposed directly.

DTOs include AccessPackageSummary, AccessPackageDetail, CreateAccessPackageRequest, UpdateAccessPackageRequest, SubscriptionPlanSummary, SubscriptionPlanDetail, CreateSubscriptionPlanRequest, UpdateSubscriptionPlanRequest, CreatePurchaseRequest, PurchaseSummary, PurchaseDetail, PaymentInitiationView, PaymentView, AccessGrantSummary, AccessGrantDetail, CreateAccessGrantRequest, CouponSummary, CouponDetail, CreateCouponRequest, UpdateCouponRequest, ValidateCouponRequest, ValidateCouponResponse.

Provider-specific raw responses stay behind the payment adapter.

---

## 47. Student purchase DTO

Student-facing purchase responses may expose purchaseId, subscriptionPlanId, packageId, plan name, purchaseDate, amount, and paymentStatus.

Do not expose createdBy, updatedBy, provider secrets, internal audit metadata, or other-user identifiers.

---

## 48. Finance purchase DTO

Finance may additionally receive purchaseId, userId, subscriptionPlanId, couponId, purchaseDate, amount, paymentStatus, payment reference, payment method, and payment date subject to permission.

Authentication secrets are never exposed.

---

## 49. Payment DTO

Provider-safe response contains only data needed by the client.

```text
purchaseId
paymentStatus
paymentReference when safe
provider checkout token or session reference when required
next action
```

The design is reviewed against the selected provider before freezing.

---

## 50. AccessGrant DTO

Student view.

```text
packageId
packageName
startDate
endDate
active
```

Staff, support, finance, and admin may receive additional operational fields according to permissions.

Do not expose other students' grant ids, private audit data, purchase secrets, or provider callback payloads unless required for an authorized operational screen.

---

## 51. Coupon DTO

Public and student validation responses reveal only valid, discount result according to approved semantics, final amount, and reason code if rejected.

Raw coupon administration metadata not needed for checkout is not returned.

---

## 52. Database constraints

Preserve all frozen constraints.

```text
AccessPackage:      UNIQUE(code)
AccessPackageCollection: UNIQUE(access_package_id, assessment_collection_id)
SubscriptionPlan:   UNIQUE(code)
PaymentRecord:      UNIQUE(purchase_id)
Coupon:             UNIQUE(code)
```

Payment reference uniqueness is applied only when confirmed compatible with the selected provider.

---

## 53. Index review

Required commerce indexes are already defined in the schema. Additional indexes require evidence.

---

## 54. Access query performance

The hottest authorization path is likely.

```text
User
  |
  v
AccessGrant
  |
  v
AccessPackage
  |
  v
AccessPackageCollection
  |
  v
AssessmentCollection
  |
  v
Assessment
```

The query is measured under realistic student load.

Recommended performance target for ordinary applicable Commerce and authorization APIs is p95 under 100 ms.

This is measured evidence, not a justification for premature caching.

---

## 55. Redis policy

Redis may cache active package catalog, subscription plans, coupon lookup where safe, and package-to-collection mappings with controlled invalidation.

Redis is not the source of truth for Purchase, PaymentRecord, AccessGrant, payment state, or entitlement.

A Redis outage does not silently cause paid users to gain or lose entitlements.

The service falls back to PostgreSQL for authoritative authorization.

---

## 56. Cache invalidation

When an AccessPackage changes, invalidate the package cache and package collection cache.

When a SubscriptionPlan changes, invalidate the plan cache.

When a Coupon changes, invalidate the coupon cache.

When an AccessGrant changes, authorization data remains PostgreSQL-authoritative.

---

## 57. Access authorization caching

Caching ALLOW and DENY decisions is higher risk than caching catalog metadata.

Default implementation caches catalog, not authorization decisions, unless load testing demonstrates a need and the TTL and invalidation semantics are explicitly approved.

---

## 58. Transaction boundaries

### 58.1 Package mapping

Single transaction: validate package, validate collection, insert junction, audit.

### 58.2 Purchase creation

Single transaction: validate plan, validate coupon, calculate amount, create purchase.

### 58.3 Payment success

Single transaction: payment state, purchase state, grant state, audit.

Provider call is not wrapped inside a long database transaction.

### 58.4 Refund

Provider call outside local transaction where necessary, then persist normalized result, update payment state, update purchase state, apply approved grant policy, audit.

The exact saga and retry strategy depends on provider behavior.

---

## 59. External payment failure model

Payment integration distinguishes provider timeout, provider explicit failure, provider success but callback delayed, provider callback duplicate, provider callback invalid, and local DB failure after provider success.

These conditions are not collapsed into one generic payment-failed state.

---

## 60. Payment reconciliation

Because external systems can succeed while the callback is delayed, Commerce requires an operational reconciliation capability.

The initial implementation can be a service or job driven by provider query APIs if available.

It compares local PaymentRecord with provider payment state and produces an operational discrepancy.

No new reconciliation table is introduced unless required and formally approved.

---

## 61. Reconciliation states

As an application-level report.

```text
MATCHED
LOCAL_PENDING_PROVIDER_SUCCESS
LOCAL_SUCCESS_PROVIDER_UNKNOWN
LOCAL_FAILED_PROVIDER_SUCCESS
REFERENCE_NOT_FOUND
AMOUNT_MISMATCH
```

These are report and diagnostic concepts, not frozen database values.

---

## 62. Scheduled jobs

Commerce background jobs may include payment reconciliation, access expiry scan, retry failed provider interaction, commerce reporting refresh, coupon catalog cache refresh, and operational anomaly scan.

Job infrastructure uses the common `background_job` table.

Long-running operations do not block transactional API requests.

---

## 63. Access expiry

Access expiry is evaluated from AccessGrant.status, start_date, end_date, and server time.

Do not depend on a cron job to make an expired grant invalid.

The authorization query is time-aware.

A background job may archive or transition records for housekeeping only if the approved lifecycle permits it.

---

## 64. Manual grant versus paid grant

Both are represented as AccessGrant.

The frozen schema does not include a grant source or type column.

Do not add `grant_source`, `grant_type`, or `purchase_id` to AccessGrant without amendment.

The provenance of a grant is recoverable through audit history, transaction lineage, and operational context.

---

## 65. Commerce lineage

```text
Purchase
   |
   +-- SubscriptionPlan
   |        |
   |        +-- AccessPackage
   |
   +-- Coupon
   |
   +-- PaymentRecord
            |
            v
       Payment status
            |
            v
        AccessGrant
```

The platform can explain how a user's access was obtained.

---

## 66. Entitlement lineage

For a paid assessment.

```text
User
  |
  v
AccessGrant
  |
  v
AccessPackage
  |
  v
AccessPackageCollection
  |
  v
AssessmentCollection
  |
  v
Assessment
  |
  v
AssessmentVersion
  |
  v
Delivery authorization
```

---

## 67. Security - Purchase ownership

For `GET /api/v1/purchases/{id}`, the service verifies that the student user equals purchase.user_id unless the requester has explicit operational, finance, or admin scope.

Frontend route guards alone are not sufficient.

---

## 68. Security - IDOR

Test all Commerce resource IDs against another student's data.

Minimum tests.

```text
Purchase ID swap
Grant ID swap
Payment purchase ID swap
Subscription plan mutation
Coupon mutation
Package mutation
Refund attempt by student
Manual grant attempt by student
```

Expected outcome: 403 or 404 according to the application's object-authorization convention.

---

## 69. Security - Payment callback

Payment callback is not a normal JWT-protected student endpoint.

It uses the selected provider's authentication or signature mechanism.

Do not accept a student JWT as the provider authenticity mechanism.

---

## 70. Security - Callback replay

Minimum controls.

```text
Provider authenticity
Reference uniqueness where supported
State transition validation
Purchase lock
Idempotency
```

Repeated success callbacks do not create multiple grants.

---

## 71. Security - Amount tampering

Reject or ignore all client attempts to modify price, amount, discount, payment status, and grant dates.

A student request never directly submits an AccessGrant end date for a paid purchase.

---

## 72. Security - Secrets

Never log gateway API secret, signature secret, private key, checkout secret, provider authorization token, or raw payment credentials.

Provider SDK request and response logging redacts sensitive fields.

---

## 73. Security - Logging

Safe operational fields.

```text
purchaseId
payment status
provider reference when non-sensitive
requestId
outcome
latency
```

Sensitive payload bodies are excluded or redacted.

---

## 74. Security - Rate limiting

Apply careful rate limiting to coupon validation, purchase creation, payment initiation, payment status polling, and refund operations.

Provider callback rate limiting does not break legitimate retry behavior.

Shared infrastructure limits are capacity-tested.

---

## 75. Frontend module structure

Recommended Next.js structure.

```text
app/
  student/
    store/
    packages/
    checkout/
    purchases/
    access/
  finance/
    purchases/
    payments/
    refunds/
    coupons/
    plans/
  staff/
    access-grants/
  admin/
    commerce/

components/commerce/
  PackageCard
  PackageDetail
  PlanSelector
  CouponInput
  PurchaseSummary
  PaymentStatus
  AccessGrantCard
  PurchaseHistory
  CommerceTable
```

---

## 76. Frontend state ownership

Server state: TanStack Query.

Local form state: React Hook Form.

Validation: Zod.

HTTP: Axios.

The frontend does not replicate payment or business rules as a second source of truth.

---

## 77. Student store

The store page supports package discovery, package details, plan selection, price display, duration display, assessment collection summary, coupon entry, purchase action.

The package catalog can be cached as ordinary content metadata. Authorization remains server-side.

---

## 78. Checkout

Checkout displays selected package, selected plan, plan duration, server-calculated amount, coupon status, and final payable amount.

The client submits `subscriptionPlanId` and `couponCode`, not the amount.

---

## 79. Payment UX

States.

```text
READY
PAYMENT_INITIATING
PAYMENT_PENDING
PAYMENT_SUCCESS
PAYMENT_FAILED
PAYMENT_REFUNDED
PAYMENT_REQUIRES_RECOVERY
```

These are UI states. The backend remains authoritative.

---

## 80. Post-payment UX

After payment confirmation.

```text
Payment SUCCESS
      |
      v
Refresh purchase
      |
      v
Refresh access
      |
      v
Refresh package and assessment catalog
      |
      v
Unlock entitled content
```

The user is not required to log out and log in for entitlement to appear.

---

## 81. Purchase history

Student purchase page uses `GET /api/v1/purchases`.

Show purchase date, plan, package, amount, and payment status.

Raw provider payloads are not displayed.

---

## 82. My access

A student entitlement page shows package, start date, end date, active or expired, and collections unlocked.

This page is a read projection over AccessGrant and package mapping.

Do not create a student_package table.

---

## 83. Finance purchase workspace

Finance workspace supports search purchase, filter status, filter date, filter plan, view payment, initiate refund, and review access impact.

Every mutation goes through Commerce application services.

---

## 84. Coupon administration

Finance and admin UI supports create coupon, edit coupon, activate or inactivate, archive, and validate.

Do not offer UI controls for unsupported concepts such as percentage mode, usage limit, per-user limit, package list, or maximum discount until the schema and business rules are extended.

---

## 85. Subscription plan administration

Admin or authorized finance and staff UI supports create plan, edit plan, activate, deactivate, and archive.

Validation checks package exists, code unique, duration valid, and price valid.

---

## 86. Package administration

Authorized staff and admin UI supports create package, edit package, map collections, activate and deactivate, and archive.

Package mapping UI uses the dedicated junction API.

---

## 87. Bundle mapping UX

```text
Package detail
    |
    v
Collections tab
    |
    v
Add collection
    |
    v
Validate
    |
    v
Persist junction
    |
    v
Invalidate cache
    |
    v
Audit
```

Duplicate mapping returns conflict.

---

## 88. Manual grant UX

```text
Find user
   |
   v
Select package
   |
   v
Set start and end
   |
   v
Validate
   |
   v
Confirm
   |
   v
Create AccessGrant
   |
   v
Audit
```

The UI warns when a grant overlaps an existing grant if the business policy defines overlap warnings.

It does not silently merge grants without an approved policy.

---

## 89. Refund UX

```text
Purchase detail
   |
   v
Refund
   |
   v
Confirm
   |
   v
Permission check
   |
   v
Current payment state check
   |
   v
Provider refund
   |
   v
Result
   |
   v
Access impact policy
   |
   v
Audit
```

Client-side status mutation is prohibited.

---

## 90. API pagination

All potentially large lists are paginated.

```text
Purchases
Access grants
Coupons
Plans
Packages
```

A student purchase history may use a smaller page size. Finance and admin tables support server-side sorting and filtering.

---

## 91. API sorting

Supported sort fields are allow-listed.

Do not interpolate raw request values into SQL.

Example safe list.

```text
purchaseDate
amount
paymentStatus
createdOn
```

---

## 92. API search

Search is controlled.

Example.

```text
Purchase id
User email
User mobile
Payment reference
Coupon code
Package code
Plan code
```

Search across another student's personal data is permission-controlled.

---

## 93. API consistency

All Commerce endpoints use `/api/v1`, JWT, UUID, standard response envelope, standard error envelope, `X-Request-Id`, and OpenAPI.

Actions use explicit operation endpoints.

---

## 94. Idempotency contract

Idempotency is mandatory for purchase creation, payment initiation, payment callback, refund, manual access grant, access revoke, and background reconciliation.

The platform's common idempotency utility is reused rather than building seven inconsistent implementations.

---

## 95. Concurrency - Purchase

Two concurrent requests may try to create the same purchase.

The service is safe under double click, browser retry, network retry, and mobile retry.

Use idempotency key and transaction isolation or locking as appropriate.

---

## 96. Concurrency - Payment callback

Two workers may process the same callback simultaneously.

Required.

```text
Lock existing PaymentRecord and Purchase
Validate current state
Perform exactly-once local state transition
```

The local transaction makes SUCCESS to grant effectively single-application.

---

## 97. Concurrency - Refund

Two operators may attempt refund simultaneously.

Required.

```text
Lock purchase and payment
Validate current state
Ensure only legal transition
Provider idempotency
```

---

## 98. Concurrency - Manual grants

Two support operators may grant the same package simultaneously.

The approved overlap policy is applied.

Detection of overlap is enforced. The policy is either reject or merge according to the approved rule.

The exact rule is documented and audited.

---

## 99. Repository rules

Repositories belong to Commerce.

An `AccessGrantRepository` is not called from AssessmentController, DeliveryController, StudentController, or any other module.

Instead.

```text
AssessmentAccessService
   |
   v
Commerce authorization contract
```

This preserves module boundaries.

---

## 100. Cross-module dependency direction

Preferred.

```text
Delivery
   |
   v
Assessment Access Contract
   |
   v
Commerce Entitlement Service
```

Not.

```text
Delivery
   |
   v
AccessGrantRepository
```

Likewise, Commerce accesses Assessment Collection through an Assessment Collection Access Contract rather than directly constructing JPA traversals across modules.

---

## 101. Eventing boundary

Commerce may emit application events such as PurchaseCreated, PaymentSucceeded, PaymentFailed, PaymentRefunded, AccessGranted, and AccessRevoked.

These are application and in-process integration events unless an external event bus is formally introduced.

Persistent event tables are not created without amendment.

---

## 102. Communication integration

Later Communication consumes commerce events to send purchase confirmation, payment success, payment failure, access granted, refund notice.

P9 publishes application events where the platform event framework exists.

P9 does not implement the full Communication domain.

---

## 103. Analytics integration

P8 analytics is downstream from transactional data.

Commerce analytics derives from Purchase, PaymentRecord, and AccessGrant.

No commerce metric replaces the transaction tables as source of truth.

Examples.

```text
Gross purchases
Successful payments
Refunds
Active grants
Package adoption
```

These are derived metrics, not new transactional truth.

---

## 104. Data retention

Historical financial and entitlement records remain traceable.

Prefer ARCHIVED over hard deletion.

Do not cascade-delete Purchase, PaymentRecord, or AccessGrant because they participate in financial and access history.

---

## 105. Deletion rules

### 105.1 AccessPackage

DELETE endpoint exists, but physical deletion is restricted by referential integrity and history rules.

If referenced by SubscriptionPlan, Purchase lineage, or AccessGrant, prefer ARCHIVED.

### 105.2 SubscriptionPlan

A plan with purchase history is not physically deleted.

### 105.3 Coupon

A coupon associated with purchases remains historically referenceable.

### 105.4 Purchase, Payment, Grant

No hard delete.

---

## 106. Financial data integrity

For every successful paid purchase.

```text
Purchase exists
PaymentRecord exists
PaymentRecord.purchase_id matches Purchase.id
PaymentRecord is SUCCESS
Purchase.payment_status is SUCCESS
AccessGrant exists or approved downstream grant action is recorded
```

Any broken chain is a reconciliation incident.

---

## 107. Entitlement integrity

For every active paid AccessGrant.

```text
User exists
Package exists
Status valid
Start and end dates valid
Package mapping remains queryable
```

The assessment authorization service denies access when package entitlement resolution fails.

---

## 108. Purchase-to-grant consistency

The frozen schema does not have a direct foreign key from AccessGrant to Purchase.

Provenance is maintained through audit, purchase and payment lineage, and operational records.

A direct FK from AccessGrant to Purchase is not added without formal schema amendment.

---

## 109. Commerce reconciliation checks

Required checks.

```text
Successful payment without grant
Active grant without expected payment or manual-grant context
Payment success with mismatched amount
Purchase success with missing payment record
Payment reference duplicated unexpectedly
Refund success with stale entitlement
Inactive package still purchasable
Inactive plan still purchasable
Duplicate package and collection mapping
Invalid coupon applied to purchase
```

---

## 110. Operational alerts

Alert conditions may include callback failure spike, payment success without grant, payment reference collision, refund provider failure, reconciliation mismatch, coupon validation error spike, high purchase API p95, high payment initiation latency, and grant creation failure after payment success.

These feed the existing observability stack.

---

## 111. Metrics

Minimum Commerce metrics.

```text
commerce.purchase.create.count
commerce.purchase.create.failure.count
commerce.payment.initiation.count
commerce.payment.callback.count
commerce.payment.callback.duplicate.count
commerce.payment.success.count
commerce.payment.failure.count
commerce.payment.refund.count
commerce.payment.refund.failure.count
commerce.access.grant.count
commerce.access.revoke.count
commerce.coupon.validation.count
commerce.coupon.validation.invalid.count
commerce.authorization.allow.count
commerce.authorization.deny.count
commerce.reconciliation.mismatch.count
```

Latency metrics.

```text
commerce.purchase.create.latency
commerce.payment.initiation.latency
commerce.payment.callback.latency
commerce.access.authorization.latency
commerce.coupon.validation.latency
commerce.refund.latency
```

---

## 112. Logs

Structured fields.

```text
requestId
userId where safe
purchaseId
paymentReference where safe
accessPackageId
subscriptionPlanId
couponId
operation
status
latency
errorCode
```

Complete payment payloads are not logged.

---

## 113. Tracing

Trace the end-to-end paid flow.

```text
POST /purchases
      |
      v
POST /purchases/{id}/payment
      |
      v
Provider
      |
      v
POST /payments/callback
      |
      v
PaymentRecord
      |
      v
Purchase
      |
      v
AccessGrant
      |
      v
Assessment authorization
```

The common request correlation model is used.

---

## 114. Testing strategy

Testing is multi-layered.

```text
Unit
Repository
Integration
API
Security
Contract
Concurrency
Provider adapter
Frontend
End-to-end
Load
Reconciliation
```

---

## 115. Unit tests - AccessPackage

Minimum.

```text
Create valid package
Duplicate code rejected
Archive behavior
Invalid status transition
Mapping validation
```

---

## 116. Unit tests - SubscriptionPlan

Minimum.

```text
Valid plan
Unknown package rejected
Duplicate code rejected
Invalid duration
Invalid price
Inactive plan purchase rejected
```

---

## 117. Unit tests - Purchase

Minimum.

```text
Create purchase
Server-side amount calculation
Coupon applied
Invalid coupon rejected
Inactive plan rejected
Duplicate request idempotency
Ownership checks
```

---

## 118. Unit tests - Coupon

Minimum.

```text
Active coupon valid
Inactive coupon invalid
Before-validity invalid
After-validity invalid
Code normalization
Price calculation
Boundary dates
Discount exceeding plan price is rejected
```

---

## 119. Unit tests - Payment

Minimum.

```text
Provider mapping
Success mapping
Failure mapping
Unknown provider state rejected
Duplicate callback
Payment reference validation
Amount mismatch
Invalid state
```

---

## 120. Unit tests - AccessGrant

Minimum.

```text
Active grant
Inactive grant
Future grant
Expired grant
Package match
Package mismatch
Multiple grants
Manual grant
Revoke policy
Null end date does not expire
Boundary inclusivity
```

---

## 121. Integration test - Full purchase

Scenario.

```text
User
 |
 v
Active plan
 |
 v
Purchase
 |
 v
Payment initiation
 |
 v
Successful callback
 |
 v
AccessGrant
 |
 v
Assessment access
```

Assert one Purchase, one PaymentRecord, one effective successful payment state, approved AccessGrant, and authorization ALLOW.

---

## 122. Integration test - Duplicate callback

Send the same callback three times.

Expected: one effective payment transition, no duplicate active grant, no duplicate purchase, and deterministic audit.

---

## 123. Integration test - Failed payment

Expected.

```text
Purchase payment_status = FAILED
PaymentRecord payment_status = FAILED
No paid AccessGrant
Assessment remains denied
```

---

## 124. Integration test - Refund

Expected after approved full refund semantics.

```text
PaymentRecord = REFUNDED
Purchase.payment_status = REFUNDED
Associated AccessGrant = INACTIVE with end_date = refund time
Audit exists
```

---

## 125. Integration test - Student isolation

Create User A purchase and User B purchase.

User A does not retrieve User B purchase, User B payment, or User B grant by manipulating IDs.

---

## 126. Security test - Callback spoofing

Attempt unsigned callback, invalid signature, wrong secret, tampered payment reference, tampered amount, and tampered purchase.

All are rejected.

---

## 127. Security test - Amount tampering

Create purchase request with an extra amount field set to 1.00.

The extra client field is ignored or rejected and does not alter stored amount.

---

## 128. Concurrency test - Callback burst

Run 100 identical callbacks against one payment.

Expected: one effective payment transition, one effective grant creation or update, and no duplicate entitlement.

---

## 129. Concurrency test - Purchase double click

Run concurrent purchase creation requests with the same idempotency key.

Expected: one Purchase and the same logical result.

---

## 130. Concurrency test - Refund double click

Run concurrent refund requests.

Expected: one effective refund and later calls observe existing refunded state, subject to provider idempotency.

---

## 131. Cache failure tests

Disable Redis.

Expected.

```text
Catalog still works from DB
Authorization still works from DB
Purchase still works
Callback still works
No entitlement corruption
```

Redis is not a single point of correctness.

---

## 132. Database failure tests

Simulate failure between payment state update and grant transaction commit.

The system preserves a recoverable reconciliation path.

A locally completed payment state is not acknowledged when the authoritative local transaction failed.

---

## 133. Provider timeout tests

Simulate payment initiation timeout, callback delay, and refund timeout.

Expected behavior follows provider contract and avoids false success.

---

## 134. Contract tests

OpenAPI contract tests verify paths, request schemas, response schemas, status codes, error envelopes, and security requirements.

Provider adapter contract tests verify normalized behavior independently from a live production provider.

---

## 135. Frontend tests

Minimum.

```text
Package listing
Plan selection
Coupon validation
Purchase creation
Payment pending
Payment success
Payment failure
Purchase history
My access
Permission gating
Finance purchase table
Refund flow
Manual grant flow
```

---

## 136. End-to-end test

Full browser test.

```text
Student login
  |
  v
Browse package
  |
  v
Choose plan
  |
  v
Validate coupon
  |
  v
Create purchase
  |
  v
Initiate payment using test gateway
  |
  v
Callback
  |
  v
Refresh purchase
  |
  v
Confirm access
  |
  v
Open entitled assessment
  |
  v
Start practice or exam according to assessment rules
```

No production provider is required for CI.

---

## 137. Test gateway

Create an in-memory or fake payment provider implementing PaymentGateway.

Capabilities.

```text
SUCCESS
FAILED
PENDING
DUPLICATE CALLBACK
TAMPERED CALLBACK
TIMEOUT
REFUND SUCCESS
REFUND FAILURE
```

This is test infrastructure, not a production payment provider.

---

## 138. Test data

Seed data.

```text
Package: SSC-MOCK-PACK
Collections: SSC-COL-A, SSC-COL-B
Plans: SSC-30-DAYS, SSC-90-DAYS
Coupon: WELCOME10

Users: student, staff, support, finance, admin
```

Deterministic UUIDs are used only in fixtures.

---

## 139. Flyway migration

P9 uses Flyway.

The commerce migration includes only schema changes required by the frozen baseline.

Do not create payment_event, refund, grant_source, purchase_status, or coupon_usage tables without approved schema amendments.

---

## 140. Migration verification

Migration tests verify tables exist, foreign keys exist, unique constraints exist, indexes exist, status values are accepted according to application contracts, and historical rows survive migration.

---

## 141. Seed and bootstrap data

For development and staging only.

```text
Sample package
Sample collection mapping
Sample plans
Test coupon
Test users
```

Production bootstrap does not create real customer entitlements without an explicit operational workflow.

---

## 142. Feature flag and rollout

Commerce is introduced behind controlled rollout where the deployment architecture supports feature flags.

Recommended logical flags.

```text
commerce.catalog.enabled
commerce.checkout.enabled
commerce.coupons.enabled
commerce.refunds.enabled
commerce.manual-grants.enabled
```

These are operational configuration concepts. They do not use new Commerce tables.

---

## 143. Pilot compatibility

Before commercial checkout is enabled, manual AccessGrant provisioning remains valid.

Existing pilot grants continue to authorize access.

Commerce does not require a Purchase for existing pilot users.

---

## 144. Migration of pilot grants

No automatic recreation of pilot grants is required.

Existing AccessGrant records are already authoritative.

A migration does not rewrite historical grants merely to make them look commercial.

---

## 145. Commerce rollout sequence

```text
1.  Deploy schema already frozen
2.  Implement package and collection mapping
3.  Implement package catalog
4.  Implement subscription plans
5.  Implement purchase service
6.  Implement coupon validation
7.  Implement payment adapter with test gateway
8.  Implement payment callback
9.  Implement AccessGrant automation
10. Implement finance operations
11. Implement refund according to policy
12. Run reconciliation
13. Run security and concurrency tests
14. Staging pilot
15. Controlled production rollout
```

---

## 146. Staging acceptance

Before production checkout.

```text
Package management works
Collection mapping works
Plan management works
Purchase creation works
Server price calculation works
Coupon validation works
Payment initiation works
Callback authentication works
Callback idempotency works
Successful payment creates grant
Failed payment creates no paid grant
Authorization works
Refund behavior works
Student isolation works
Audit works
Metrics work
Reconciliation works
```

---

## 147. Commerce load testing

Required scenarios.

### 147.1 Package browse

High read concurrency. Measure p95 latency, DB CPU, Redis hit rate.

### 147.2 Purchase burst

Simulate hundreds or thousands of users purchasing concurrently. Measure purchase transaction latency, DB lock waits, connection pool, and idempotency behavior.

### 147.3 Callback burst

Simulate provider callback burst and duplicate callbacks. Measure throughput, p95 latency, DB contention, duplicate rate, and grant consistency.

### 147.4 Grant authorization burst

Simulate many students opening assessments concurrently. Measure authorization latency, DB query latency, and cache behavior.

---

## 148. Performance acceptance

Commerce remains compatible with platform performance objectives.

Applicable API p95 under 100 ms.

External payment provider latency is measured separately from local API processing.

Measure separately local request processing, provider round trip, callback processing, grant creation, and authorization.

---

## 149. Database connection management

Commerce uses the same production connection-management strategy as the core platform.

```text
Application
   |
   v
PgBouncer
   |
   v
PostgreSQL
```

Connection pool sizes are derived from load-test evidence, DB capacity, PgBouncer mode, worker count, and API concurrency.

---

## 150. Background worker capacity

Commerce workers do not starve snapshot preparation, analytics refresh, email jobs.

Use independent worker concurrency controls where the platform worker architecture supports them.

---

## 151. Observability dashboard

Recommended Commerce dashboard panels.

```text
Purchase rate
Purchase success rate
Payment success rate
Payment failure rate
Pending payment count
Refund count
Callback throughput
Duplicate callback count
Grant creation count
Grant authorization allow rate
Grant authorization deny rate
Coupon validity rate
Commerce p95
Provider latency
Reconciliation mismatch count
```

---

## 152. Operational runbook - Payment pending

When a purchase is pending.

```text
Check Purchase
   |
   v
Check PaymentRecord
   |
   v
Check provider reference
   |
   v
Check provider state
   |
   v
Reconcile
   |
   v
Apply normalized state
   |
   v
Confirm AccessGrant
```

Do not manually mark payment SUCCESS without a verified source.

---

## 153. Operational runbook - Payment success without access

```text
Check payment success
   |
   v
Check Purchase
   |
   v
Check AccessPackage
   |
   v
Check AccessGrant creation transaction
   |
   v
Check audit
   |
   v
Reconcile
   |
   v
Repair entitlement through Commerce service
```

Direct insert of the grant through SQL in production is prohibited.

---

## 154. Operational runbook - Duplicate callback

```text
Locate PaymentRecord
   |
   v
Compare reference
   |
   v
Inspect current state
   |
   v
Confirm idempotent outcome
   |
   v
No second grant
```

A duplicate callback is not treated as a payment failure.

---

## 155. Operational runbook - Refund

```text
Verify purchase ownership and scope
   |
   v
Check payment state
   |
   v
Initiate provider refund
   |
   v
Verify provider outcome
   |
   v
Persist REFUNDED
   |
   v
Apply approved access policy
   |
   v
Audit
```

---

## 156. Operational runbook - Manual grant

```text
Identify user
   |
   v
Validate package
   |
   v
Validate dates
   |
   v
Create AccessGrant
   |
   v
Audit
   |
   v
Verify assessment authorization
```

---

## 157. Currency and rounding policy

The frozen schema supports `price NUMERIC(10,2)`, `amount NUMERIC(10,2)`, and `discount_value NUMERIC(10,2)`.

The platform operates on a single currency per deployment.

Rounding uses HALF_UP at 2 decimals.

The application uses BigDecimal for money and never binary floating-point.

Schema expansion for multi-currency is not introduced without formal amendment.

---

## 158. Assessment collection resolution

The frozen relationship `collection_assessment` uses `collection_id` and `assessment_id`.

The Commerce entitlement resolver calls the Assessment domain contract to determine which published AssessmentVersion is currently accessible.

Commerce does not duplicate current-version logic.

---

## 159. Package to collection API

```http
GET    /api/v1/access-packages/{packageId}/collections
POST   /api/v1/access-packages/{packageId}/collections
DELETE /api/v1/access-packages/{packageId}/collections/{collectionId}
```

Add request.

```json
{
  "collectionId": "<uuid>"
}
```

Duplicate mapping returns 409.

---

## 160. Payment callback idempotency contract

Approved decision.

```text
The selected provider guarantees a globally unique payment reference.
The payment_record.payment_reference column receives a UNIQUE constraint.
Duplicate callbacks with the same reference are idempotent.
```

If the provider later changes reference semantics, a formal schema amendment is required before implementation changes.

---

## 161. Refund and access revocation policy

Approved decision.

```text
Full refunds only.
Multiple refund events are not independently persisted.
On refund, associated AccessGrant is INACTIVE.
end_date of the grant is set to the refund time.
```

A richer refund ledger requires a formal schema amendment.

---

## 162. Grant date and merge policy

Approved decision.

```text
Grant start date = purchase_date
Grant end date = purchase_date + duration_days
For an existing active grant for the same user and package,
end_date = max(existing_end_date, purchase_date) + duration_days
```

Manual grants use operator-supplied dates.

---

## 163. Duplicate purchase policy

The database does not enforce a uniqueness constraint such as `UNIQUE(user_id, subscription_plan_id)`.

The application permits multiple purchases over time, subject to the grant date and merge policy.

---

## 164. Coupon semantics

Frozen decision.

```text
discount_value is a fixed amount in the platform currency.
Discount does not exceed the plan price.
Result is never negative.
HALF_UP at 2 decimals.
```

A future percentage or usage-limited mode requires a formal schema amendment.

---

## 165. Access validity window

Frozen decision.

```text
start_date is inclusive.
end_date is inclusive.
end_date may be NULL when the grant does not expire.
Authorization uses server time.
```

---

## 166. Payment provider

The initial implementation may use a fake provider for staging.

Provider-specific implementation is isolated behind the PaymentGateway contract.

Concrete provider selection is an operational decision and is not baked into the domain schema.

---

## 167. Contract gap closure summary

```text
Payment provider selection contract
AccessGrant validity window
Coupon discount semantics
Duplicate purchase policy
Purchase-to-grant date and merge policy
Refund and access revocation policy
Payment callback idempotency persistence
Package-to-collection API contract
Assessment collection resolution
Currency and rounding policy
```

The design in this document closes each area without adding undocumented schema.

---

## 168. No schema invention rule

Do not silently add:

```text
payment_event
refund
coupon_usage
purchase_status
grant_type
grant_source
wallet
invoice
tax_line
provider_event
```

If any of these are required for the approved product contract, first produce an architecture and schema amendment.

---

## 169. No API invention rule

Do not silently add new endpoints.

The frozen endpoints support the required behavior.

If an implementation requires an additional endpoint, first produce a formal amendment.

---

## 170. Commerce anti-patterns

Reject.

```text
Client-side price calculation
Payment-based assessment authorization
Purchase treated as AccessGrant
PaymentRecord treated as AccessGrant
Frontend unlocking paid content
Redis as authoritative payment or entitlement store
Duplicate grant creation on retry
Hard delete of financial history
Cross-module repository access
Executable configuration
```

---

## 171. P9 implementation task breakdown

```text
P9-01  Commerce module skeleton
P9-02  AccessPackage
P9-03  AccessPackageCollection
P9-04  Package mapping API
P9-05  SubscriptionPlan
P9-06  Coupon
P9-07  Coupon calculator
P9-08  Purchase
P9-09  PaymentRecord
P9-10  PaymentGateway abstraction
P9-11  Payment initiation
P9-12  Payment callback with idempotency
P9-13  AccessGrant
P9-14  Assessment authorization integration
P9-15  Refund
P9-16  Finance workspace
P9-17  Student store
P9-18  Reconciliation
P9-19  Redis cache for catalog only
P9-20  Observability
P9-21  Security tests
P9-22  Concurrency tests
P9-23  End-to-end test
P9-24  Production readiness
```

Execute one task at a time with checkpoints.

---

## 172. P9-01 to P9-05 acceptance

```text
[ ] Module skeleton
[ ] AccessPackage CRUD and audit
[ ] AccessPackageCollection mapping
[ ] Package mapping API
[ ] SubscriptionPlan CRUD
[ ] RBAC enforced
[ ] Tests pass
```

---

## 173. P9-06 to P9-10 acceptance

```text
[ ] Coupon CRUD
[ ] Coupon calculator
[ ] Fixed-amount semantics enforced
[ ] Discount never exceeds plan price
[ ] Purchase creation with server-side amount
[ ] PaymentRecord repository
[ ] PaymentGateway abstraction
[ ] Fake provider
```

---

## 174. P9-11 to P9-15 acceptance

```text
[ ] Payment initiation
[ ] Payment callback with authenticity verification
[ ] Callback idempotency
[ ] Amount verification
[ ] AccessGrant creation and merge policy
[ ] Assessment authorization integration
[ ] Refund according to policy
[ ] Access revocation on refund
```

---

## 175. P9-16 to P9-24 acceptance

```text
[ ] Finance workspace
[ ] Student store
[ ] Reconciliation
[ ] Redis cache for catalog only
[ ] Observability
[ ] Security tests
[ ] Concurrency tests
[ ] End-to-end test
[ ] Production readiness
[ ] CI green
```

---

## 176. Recommended implementation prompt

```text
You are implementing P9 Commerce and Access Management for the Smart
Competitive Examination Platform.

Read the full specification package before modifying the repository.

P9 owns:
AccessPackage
AccessPackageCollection
SubscriptionPlan
Purchase
PaymentRecord
AccessGrant
Coupon
Pricing
Payment orchestration
Refunds
Entitlement

NON-NEGOTIABLE:
1.  Do not invent database tables.
2.  Do not silently add columns.
3.  Do not silently add endpoints.
4.  AccessGrant is the authorization source of truth.
5.  Payment-based assessment authorization is prohibited.
6.  Client never determines the final price.
7.  Money uses BigDecimal and NUMERIC.
8.  Payment callback is idempotent.
9.  Provider-specific code stays behind PaymentGateway.
10. Do not add payment-event, refund, coupon-usage, or grant-source
    tables without an approved amendment.
11. Do not automatically merge purchases or grants without the
    approved policy.
12. Do not expose provider secrets.
13. Student APIs enforce object-level authorization.
14. Existing pilot and manual AccessGrants remain valid.
15. Commerce is post-pilot; checkout is not a prerequisite for existing
    pilot access.
16. Cross-module repositories are not called directly.
17. Assessment access uses a domain and service contract.
18. Redis is cache only, never financial or entitlement source of truth.
19. All important operations are audited.
20. External callback and mutation paths are idempotent.
21. Historical commercial records are not hard-deleted.
22. Flyway owns schema changes.
23. Stop and report rather than silently resolving frozen-contract
    gaps.

Task order:
P9-01 through P9-24.

For each task:
- Inspect current repository first.
- Implement the smallest correct change.
- Run focused tests.
- Run PostgreSQL integration tests.
- Report changed files.
- Report API impact.
- Report schema impact.
- Report unresolved contract issues.
- Stop before the next high-risk task.

Before merge:
- Unit tests
- Integration tests
- Security tests
- Concurrency tests
- End-to-end test
- Migration verification
- OpenAPI verification
- Audit verification
- Callback idempotency verification
- Grant merge policy verification
- Refund policy verification
- CI green
```

---

## 177. Review questions

```text
What is the authorization source of truth?
Can a purchase alone create access?
Can a payment alone create access?
Is the final price server-calculated?
Are coupon semantics fixed-amount only?
Can two identical callbacks create two grants?
Can two refund requests create two refunds?
Is the refund policy applied without a schema amendment?
Is the grant merge policy applied?
Is the payment provider isolated?
Is the callback authenticated?
Are provider secrets protected?
Is student data isolated?
Is the pilot grant path preserved?
Does Redis matter for correctness?
Is the schema unchanged?
```

---

## 178. Final engineering principle

> A Purchase is not an AccessGrant. A PaymentRecord is not an AccessGrant. A successful browser payment state is not authorization. AccessGrant is the authorization source of truth.

The commercial flow.

```text
Catalog
   |
   v
AccessPackage
   |
   v
SubscriptionPlan
   |
   v
Purchase
   |
   v
PaymentRecord
   |
   v
AccessGrant
   |
   v
Assessment authorization
```

---

## 179. P9 handoff

P9 hands the rest of the platform.

```text
Commerce application events
Commerce audit history
Commerce read models
Commerce observability
Commerce reconciliation tooling
Commerce security posture
```

Communication consumes events through a clean contract. Analytics consumes transactions through read projections. Administration presents operations without bypassing services.

---

## 180. Document status

**Status:** IMPLEMENTATION-READY.

**Phase:** P9 — Commerce and Access Management.

**Next implementation document:** P10 — Communication and Notification.