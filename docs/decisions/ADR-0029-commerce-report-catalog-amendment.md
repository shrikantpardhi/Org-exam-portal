# ADR-0029 — Commerce report catalog amendment: subscriptions, coupons, refunds

- **Status:** Accepted
- **Phase:** P13 — Reporting (catalog amendment raised by GAP-141a)
- **Spec:** `specs/22-P13-Reporting-V1.5.md` §11 (catalog), §55 (commerce reports), §59 (subscription
  report), §60 (revenue summary), §61 (coupon activity), §62 (refund activity), §107 (report registry)
- **Closes:** GAP-141a
- **Migration:** none

## Context

`22-P13 §55` lists seven candidate commerce reports:

```text
Purchases
Payments
Access Grants
Subscription Summary
Revenue Summary
Coupon Activity
Refund Activity
```

The frozen `ReportType` catalog registered four of them — `COMMERCE_PURCHASES`,
`COMMERCE_PAYMENTS`, `COMMERCE_ACCESS_GRANTS`, `COMMERCE_REVENUE_SUMMARY` — so Subscription
Summary, Coupon Activity and Refund Activity had no identifier and could not be requested.

`§11` and `§107` make the report registry a frozen contract: adding a constant changes both the
catalog and the `GET /reports/catalog` payload. AGENTS.md rule 20 forbids resolving that silently,
which is why this is an ADR rather than a code change.

**What the schema actually supports matters more than what §55 names.** §55 says "candidate" reports,
and two of the three have no underlying model:

| Report | Underlying data | Available? |
|---|---|---|
| Subscription Summary | `subscription_plan` (code, name, duration_days, price, status) | Yes — plan level only |
| Coupon Activity | `coupon` + `purchase.coupon_id` | Yes |
| Refund Activity | **no `refund` table**; only `purchase.payment_status = 'REFUNDED'` and `payment_record.payment_status = 'REFUNDED'` | Partly — refund *state*, not a refund entity |

There is no `subscription` table either: the platform sells time-boxed plans and records the grant in
`access_grant`, not a recurring subscription.

**§59 is explicit about this.** "A customer-level recurring billing history model is not invented if
not present in the database." **§62** says refund status "is sourced from the Commerce-owned payment
and refund contract". Both instruct us to report what exists rather than synthesize a model.

## Decision

Add three `ReportType` constants. Each reuses the existing `REPORT_COMMERCE_READ` permission, which
already exists and is already granted to `ADMIN` and `FINANCE` (`V24`); no new permission code is
needed and none is added.

```text
COMMERCE_SUBSCRIPTIONS   Subscription Summary    filters: from, to
COMMERCE_COUPONS         Coupon Activity         filters: status, from, to
COMMERCE_REFUNDS         Refund Activity         filters: from, to
```

**Permission.** `REPORT_COMMERCE_READ` for all three. These are financial-scope reports; §79 scopes
`FINANCE` to commerce reporting and grants it no operational or executive scope, and that split is
unchanged. Adding a fourth commerce permission would fragment one capability across four codes for no
authorization benefit.

**Filter schema.** Only filters the provider genuinely honours are declared, because
`ReportRequestValidator` rejects any key outside the schema and a declared-but-ignored filter would
silently return unfiltered data:

- `from` / `to` — every commerce report is date-bounded (§7.3, §7.6). `subscription_plan` and
  `coupon` both extend `BaseEntity`, so both carry `created_on` and a creation window is meaningful
  and cheap.
- `status` — `COMMERCE_COUPONS` only. A coupon has a lifecycle `status`
  (`ACTIVE`/`INACTIVE`/`ARCHIVED`), and callers routinely want "which coupons are still live". A
  subscription plan has the same lifecycle, but the plan set is small and bounded, so the report lists
  plans with their status as a **column** rather than as a filter — a plan catalogue is short enough
  that filtering it adds nothing over reading it.
- No `userId` filter on any of the three. Purchase-level user scoping already exists on
  `COMMERCE_PURCHASES` and `COMMERCE_ACCESS_GRANTS`; subscription, coupon and refund reports are
  administrative financial views, and a per-user filter would invite cross-user reads that §76
  scoping is designed to prevent.

**Provider reads.** Two read methods are added to `ReportingCommerceCatalog` (the Commerce-owned
contract), keeping the "Reporting never reads a Commerce repository" rule of §10:

```java
List<SubscriptionPlanRow> subscriptionPlans(int limit);
List<CouponRow> coupons(String status, int limit);
List<RefundRow> refunds(Instant from, Instant to, int limit);
```

`refunds` is derived from the authoritative refund **state** already frozen on `purchase` and
`payment_record` — no refund entity is invented, and no model is added to the database.

**`§60` revenue semantics are not touched.** The existing `COMMERCE_REVENUE_SUMMARY` provider groups
by authoritative payment state and does not equate payment totals with accounting revenue. The refund
report reports refund *state* rows and deliberately does **not** define a net-revenue figure; §60's
approved accounting definition stays the single place revenue is computed.

**No catalog endpoint changes beyond the three entries.** The payload shape of
`GET /reports/catalog` is unchanged; three more entries appear to callers holding
`REPORT_COMMERCE_READ`.

## Consequences

- `ReportType` grows from 22 to 25 entries. `P13ProjectionProvidersTest` asserts every type has
  exactly one provider, so all three need a provider in the same change.
- `GET /reports/catalog` advertises three more types to `ADMIN` and `FINANCE` only. A student, a
  `STAFF` user and a `SUPPORT` user see no change, because none holds `REPORT_COMMERCE_READ`.
- The refund report describes refund **state**, not refund lifecycle events. If the platform ever
  gains a real refund entity, that provider must be revisited — recorded in `docs/known-gaps.md`
  rather than pre-empted here.
- Subscription reporting is plan-level. Customer-level subscription history remains unavailable by
  design, per §59.

## Verification

- `backend/src/test/java/com/company/exam/reporting/projection/P13ProjectionProvidersTest.java`
  proves the three providers project real rows and that every `ReportType` value still has exactly one
  provider.
- `backend/src/test/java/com/company/exam/integration/reporting/P13CommerceReportIntegrationTest.java`
  generates all three through the HTTP surface as `ADMIN`, checks the CSV render, and proves a student
  and a `STAFF` user are refused.
