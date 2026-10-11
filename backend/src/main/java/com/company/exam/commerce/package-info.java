/**
 * Commerce module boundary.
 *
 * <p>Owns AccessPackage, AccessPackageCollection, SubscriptionPlan, Purchase, PaymentRecord,
 * AccessGrant, and Coupon. Responsible for products, subscriptions, purchases, payment lifecycle,
 * refunds, coupons, and authorization grants.
 *
 * <p>AccessGrant is the authorization source of truth; payment success alone never grants access.
 * P0 establishes this boundary only; the module is implemented in P9.
 *
 * <p>See {@code specs/06-Architecture-Freeze-V1.5.md §38}.
 */
package com.company.exam.commerce;
