# ADR-0021 — P9 Commerce: pricing, payment, and entitlement decisions

- **Status:** Accepted
- **Phase:** P9 — Commerce & Access Management
- **Spec:** `specs/18-P9-Commerce-V1.5.md`
- **Preserves:** the frozen commerce schema (seven tables), the frozen endpoints, and the AccessGrant
  authorization boundary.

## Context

P9 introduces paid access. The frozen schema already fixes the seven tables, the status vocabularies,
the unique constraints, and the API surface. This ADR records the implementation decisions the
specification left open. No table or column is added.

## Decisions

1. **Separation of concepts.** SubscriptionPlan (offer), AccessPackage (entitlement bundle),
   Purchase (commercial event), PaymentRecord (payment transaction), and AccessGrant (authorization)
   stay separate. **AccessGrant is the only authorization source of truth**; a Purchase or a
   PaymentRecord never grants access (`§3.1, §3.2, §9.1`).

2. **Server-side pricing.** `POST /purchases` ignores any client price/subtotal/discount/total. The
   amount is computed by `PricingCalculator` from the authoritative plan price and the validated
   coupon, using `BigDecimal` with HALF_UP at 2 decimals. The coupon discount is a **fixed amount**,
   clamps at the plan price, and never yields a negative total (`§11.1, §15.1, §32`). Only
   `purchase.amount` is persisted (`§15.2`).

3. **Coupon validation.** Codes are normalized to upper case; status must be ACTIVE and the server
   time must fall within the inclusive `valid_from`/`valid_to` window. The validate response is a
   convenience only — the same rules are re-run at purchase creation (`§15.1, §33`). No usage
   counters exist (`§34`).

4. **Payment provider boundary.** Provider-specific code lives behind `PaymentGateway`. Only a
   deterministic reference gateway (`TestPaymentGateway`) ships with the platform; a real provider is
   a separate adapter. The reference gateway performs **real HMAC callback authenticity
   verification** so the security boundary is exercised, not bypassed (`§13, §22, §70, §137`).
   Provider payloads and secrets never reach the domain tables or DTOs (`§13.1, §72`).

5. **Callback idempotency and amount verification.** The callback is public (no JWT) and is
   authenticated by the provider signature only (`§69`). It correlates
   `payment_reference → PaymentRecord → Purchase → SubscriptionPlan → AccessPackage` and verifies the
   amount before any entitlement change (`§22.1, §71`). A repeated success callback returns the
   already-applied state and **never creates a second grant** (`§21, §70`). The whole transition runs
   in one transaction; any failure rolls back and activates no grant (`§23`).

6. **Grant creation and merge policy.** On payment success: `start_date = purchase_date`,
   `end_date = purchase_date + plan.duration_days`. If an ACTIVE grant for the same user and package
   exists, its `end_date` is extended to `max(existing_end, purchase_date) + duration`; a grant with a
   null end date never expires and is never shortened (`§19`). Manual grants keep operator dates
   (`§14.4`).

7. **Refund and revocation.** Only a successful payment can be refunded. The purchase and payment
   rows are locked, the provider refund is confirmed, both rows become REFUNDED, and every ACTIVE
   grant for the user and package becomes INACTIVE with `end_date` set to the refund time — an
   earlier end date is never extended (`§20, §26, §97`). A repeat refund is idempotent.

8. **Entitlement resolution.** `EntitlementResolver` is the contract other modules consume; they
   never query the AccessGrant repository (`§99, §100`). It is time-aware (status, start inclusive,
   end inclusive, null end never expires) and resolves assessment entitlement through the package →
   collection → assessment mapping (`§14.2, §9.1, §63`).

9. **Reconciliation.** `ReconciliationService` compares local payment state with a provider query
   (when the provider supports one) and reports application-level states. No reconciliation table is
   introduced (`§60, §61`).

10. **Pilot compatibility.** Existing manual/pilot grants are untouched: no migration rewrites
    historical grants, and manual grants continue to authorize access without a purchase
    (`§143, §144`).

## Consequences

- Duplicate callbacks, amount tampering, and student isolation are verified end-to-end by
  `CommerceIntegrationTest`; the fixed-amount pricing rules by `DefaultPricingCalculatorTest`.
- Purchase-creation idempotency is a service-level dedupe of the existing open purchase, because no
  idempotency store is frozen (GAP-098).
- The entitlement contract is implemented but not yet enforced by Delivery/Assessment, because the
  frozen package has no "free assessment" configuration field (GAP-100).
- Only the reference gateway ships; a real provider adapter is a separate integration (GAP-099).
- The commerce frontend (student store, finance workspace) is deferred (GAP-104).

## Contract gaps discovered

Recorded in `docs/known-gaps.md` (GAP-097 … GAP-105). None was silently resolved.
