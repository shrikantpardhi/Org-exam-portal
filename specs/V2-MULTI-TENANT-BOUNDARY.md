# Smart Competitive Examination Platform
## V2 Multi-Tenant Boundary

**Version:** 1.5 reference
**Status:** Future architecture boundary note
**Purpose:** This file defines the boundary between the current single-organization deployment model and a future multi-tenant expansion.

---

## 1. Purpose

This file records the future multi-tenant expansion as a distinct, versioned architecture change.

It does not introduce any tenant-aware structure into the current baseline.

It does not alter the frozen single-organization contracts.

It exists to prevent accidental mixing of current and future design.

---

## 2. Current deployment model

The platform is one organization per deployment.

That model is preserved across every V1.x release.

There is one organization context for the running deployment.

The V1 domain model, schema, and APIs are not designed as tenant-isolated SaaS infrastructure.

---

## 3. What the current deployment includes

```text
One active organization context per deployment
One shared academic master catalog per deployment
One shared question bank per deployment
One shared assessment catalog per deployment
One shared commerce catalog per deployment
One shared reporting scope per deployment
Organization ownership recorded on identity and administration records where the domain requires it
```

The current deployment does not include:

```text
Tenant-membership model
Tenant routing
Cross-tenant authorization
Tenant-level billing isolation
Tenant-level academic partitioning
SaaS control plane
Tenant-aware reporting redesign
V2 API contracts
V2 tenant database migrations
```

---

## 4. Future multi-tenant objective

A future V2 may convert the platform into a multi-tenant SaaS platform serving multiple independent organizations in a shared deployment.

This is a distinct architecture version.

It is not implemented in V1.5.

---

## 5. V2 design questions

Before implementation, V2 resolves the following questions.

### 5.1 Tenant ownership and membership

```text
How is a tenant defined?
How do users belong to tenants?
Can a user belong to multiple tenants?
Are tenant memberships hierarchical?
```

### 5.2 Tenant resolution and request context

```text
How is the active tenant resolved for a request?
Is it derived from the URL, the header, the token, or another signal?
How is tenant context propagated to service calls?
```

### 5.3 Tenant isolation enforcement

```text
Is isolation enforced at the service layer?
At the repository layer?
At the database layer?
At the infrastructure layer?
```

### 5.4 Academic, content, and assessment ownership

```text
Are academic masters tenant-owned, shared, or hybrid?
Are question banks tenant-owned?
Are assessment catalogs tenant-owned?
```

### 5.5 Tenant-scoped analytics and reporting

```text
How are analytics and reports scoped to a tenant?
Can tenants compare against a shared baseline?
How are cross-tenant reports authorized?
```

### 5.6 Tenant-scoped commerce and billing

```text
Are purchases, payments, and grants tenant-scoped?
Is billing per tenant or per user?
How are taxes and invoicing handled per tenant?
```

### 5.7 Tenant-aware audit and support operations

```text
How is audit scoped to a tenant?
What is the support scope for tenant-level operations?
How is cross-tenant support access controlled?
```

### 5.8 Migration of the current dataset

```text
How is the current single-organization dataset migrated into a V2 model?
Is a synthetic tenant created for the existing organization?
What is the cutover strategy?
```

### 5.9 API compatibility and versioning

```text
Is V2 a new API version under /api/v2?
How is the transition handled for clients?
What is the deprecation schedule for /api/v1?
```

### 5.10 Operational isolation and quotas

```text
How is noisy-neighbor protection enforced?
How are quotas, rate limits, and capacity allocations per tenant managed?
What is the SLA per tenant?
```

---

## 6. V2 rules

```text
V2 starts from the validated V1.x behavior.
V2 preserves examination delivery correctness.
V2 preserves scoring determinism.
V2 preserves access authorization correctness.
V2 preserves auditability.
V2 preserves the reproducible attempt snapshot model.
V2 does not weaken any of these for the sake of multi-tenancy.
```

---

## 7. What V2 does not change

V2 does not change:

```text
The content representation contract
The exam engine and CDUI contract
The delivery snapshot model
The evaluation and answer-key model
The AccessGrant authorization principle
The audit atomicity principle
The audit immutability principle
The general-purpose background_job model
```

These remain stable across versions.

---

## 8. What V2 introduces

V2 introduces tenant-scoped expansion of:

```text
Domain model
Database schema
API contracts
Authorization rules
Routing and tenant resolution
Reporting scope
Operational controls
Migration strategy
```

Every one of these is a versioned expansion.

---

## 9. What V2 does not introduce

V2 does not:

```text
Replace the modular monolith
Replace PostgreSQL
Replace the frontend
Replace the delivery engine
Replace the evaluation engine
Replace the audit model
Replace the content representation
Replace the ExamEngine abstraction
```

V2 is an expansion of the product model, not a replacement of the current architecture.

---

## 10. V2 and the current baseline

The V1.5 baseline is complete at the specification level.

It remains the current deployment model.

It remains the current implementation target.

V2 is a future architecture version, not part of the current execution plan.

---

## 11. Compatibility preservation

Even though V1.5 is single-organization, the implementation avoids assumptions that would block a future V2 expansion.

Preserved compatibility patterns.

```text
Organization context passed explicitly through service contracts
No hard-coded organization name or id in business code
No global singleton business values baked into Java classes
No assumed single-user identity across the platform
No tenant-blocking shortcuts in the schema
```

These patterns do not implement multi-tenancy. They simply avoid assumptions that would make it harder later.

---

## 12. Boundary statement

The current platform is one organization per deployment.

The current specification does not introduce multi-tenant SaaS infrastructure.

A future V2 will be a separately versioned expansion.

This file records the boundary.

It does not authorize any tenant-aware change in the current baseline.

---

## 13. Document status

**Status:** Boundary note.

**Scope:** Future architecture reference.

**Not part of the current implementation plan.**