# P9 — Commerce & Access Management: Testing Notes

Phase specification: `specs/18-P9-Commerce-V1.5.md`.

## Scope verified

- Seven frozen commerce tables and their unique/foreign-key constraints and indexes.
- Server-side pricing with a fixed-amount coupon; the client never determines the final price.
- Payment initiation, callback authenticity, amount verification, and callback idempotency.
- AccessGrant creation with the frozen date/merge policy, and refund revocation.
- Entitlement resolution (package → collection → assessment).
- Student isolation on purchases, payments, and grants.

## Unit tests

| Test | What it proves |
|---|---|
| `commerce/pricing/DefaultPricingCalculatorTest` | fixed discount subtracted from the price; discount clamps at the price; never negative; no coupon means full price |

Run: `cd backend && ./mvnw test -Dtest=DefaultPricingCalculatorTest`.

## Integration tests

| Test | What it proves |
|---|---|
| `CommerceMigrationIntegrationTest` | seven tables, frozen unique/FK constraints, required indexes, no unapproved tables, permission seed |
| `CommerceIntegrationTest.purchaseAppliesTheServerCalculatedCouponDiscount` | amount = plan price − fixed coupon, computed server-side |
| `CommerceIntegrationTest.purchaseRejectsAClientSuppliedAmount` | a client-supplied `amount`/`discount` is rejected |
| `CommerceIntegrationTest.duplicateCallbackCreatesExactlyOneGrant` | **the duplicate-callback verification**: one grant, second callback `duplicate=true`, `canAccessAssessment` true |
| `CommerceIntegrationTest.callbackWithATamperedAmountIsRejectedAndGrantsNothing` | **the amount-tampering verification**: `AMOUNT_MISMATCH`, no grant, purchase stays PENDING |
| `CommerceIntegrationTest.callbackWithAnInvalidSignatureIsRejected` | unauthentic callback → 403, no grant |
| `CommerceIntegrationTest.failedPaymentCreatesNoGrant` | failure never creates entitlement |
| `CommerceIntegrationTest.refundRevokesTheGrantAndIsIdempotent` | grant → INACTIVE with `end_date` set; repeat refund idempotent; access denied afterwards |
| `CommerceIntegrationTest.studentsCannotReadOrActOnAnotherStudentsPurchase` | **the student-isolation verification**: cross-student purchase/payment reads are 404, refund is 403, grant lists are self-scoped |

Run: `cd backend && ./mvnw test -Dtest="*Commerce*"`.

## Manual/curl smoke

```bash
# Catalog (admin/finance)
curl -X POST -H "Authorization: Bearer <admin>" -H 'Content-Type: application/json' \
     -d '{"code":"SSC-MOCK-PACK","name":"SSC Mock Pack"}' $BASE/api/v1/access-packages
curl -X POST -H "Authorization: Bearer <admin>" -H 'Content-Type: application/json' \
     -d '{"collectionId":"<uuid>"}' $BASE/api/v1/access-packages/<packageId>/collections
curl -X POST -H "Authorization: Bearer <admin>" -H 'Content-Type: application/json' \
     -d '{"accessPackageId":"<packageId>","code":"SSC-90","name":"SSC 90 days","durationDays":90,"price":999.00}' \
     $BASE/api/v1/subscription-plans
curl -X POST -H "Authorization: Bearer <admin>" -H 'Content-Type: application/json' \
     -d '{"code":"WELCOME10","name":"Welcome","discountValue":100.00}' $BASE/api/v1/coupons

# Student checkout
curl -X POST -H "Authorization: Bearer <student>" -H 'Content-Type: application/json' \
     -d '{"subscriptionPlanId":"<planId>","couponCode":"WELCOME10"}' $BASE/api/v1/purchases
curl -X POST -H "Authorization: Bearer <student>" $BASE/api/v1/purchases/<purchaseId>/payment

# Provider callback (signature, not a JWT)
curl -X POST -H 'Content-Type: application/json' \
     -d '{"paymentReference":"<ref>","providerStatus":"CAPTURED","amount":899.00,"currency":"INR","signature":"<hmac>"}' \
     $BASE/api/v1/payments/callback

# Access and refund
curl -H "Authorization: Bearer <student>" $BASE/api/v1/access-grants
curl -X POST -H "Authorization: Bearer <finance>" $BASE/api/v1/purchases/<purchaseId>/refund
```

## Not exercised / deferred

- A real payment provider adapter (only the reference `TEST` gateway ships) — GAP-099.
- Delivery/Assessment enforcement of the entitlement contract — GAP-100.
- Scheduled reconciliation, access-expiry housekeeping, and the finance/student frontends — GAP-101, GAP-104.
- Rate limiting on coupon validation / purchase / payment — GAP-105.
