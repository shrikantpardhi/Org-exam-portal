package com.company.exam.delivery.application;

import com.company.exam.assessment.entity.ScheduleMode;
import com.company.exam.commerce.api.AccessDecision;
import com.company.exam.commerce.api.EntitlementResolver;
import com.company.exam.delivery.exception.PaidContentAccessDeniedException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The paid-content gate on the Start Exam path
 * ({@code specs/14-P5-Delivery-V1.5.md §19}; {@code specs/18-P9-Commerce-V1.5.md §3.2, §16, §28.1};
 * ADR-0026).
 *
 * <p>Delivery asks the Commerce entitlement contract whether the authenticated principal may access the
 * assessment. It never queries the AccessGrant repository itself
 * ({@code specs/18-P9-Commerce-V1.5.md §99, §100}).
 *
 * <p>The decision is server-side: it derives only from the authenticated user and persisted state, the
 * request carries no access input, and the mode is copied from the schedule by the preparation worker
 * ({@code specs/14-P5-Delivery-V1.5.md §94}), so a client cannot switch a paid assessment into an
 * ungated mode.
 */
@Service
public class EntitlementAccessGate {

    private final EntitlementResolver entitlementResolver;

    public EntitlementAccessGate(EntitlementResolver entitlementResolver) {
        this.entitlementResolver = entitlementResolver;
    }

    /**
     * Rejects the start when the assessment is paid content the user is not currently entitled to.
     *
     * <p>PRACTICE schedules are not entitlement-gated (ADR-0026, Decision 3): a practice attempt is a
     * rehearsal of published content rather than a scored sitting, and the entitlement in this platform
     * attaches to the official exam attempt. A null mode is treated as EXAM, matching the preparation
     * worker's default ({@code specs/14-P5-Delivery-V1.5.md §94}).
     *
     * @param serverNow the authoritative server time used for grant validity
     * @throws PaidContentAccessDeniedException with {@code ACCESS_DENIED_PAID_CONTENT} (HTTP 403)
     */
    public void assertAccess(UUID userId, UUID assessmentId, ScheduleMode mode, Instant serverNow) {
        if (mode == ScheduleMode.PRACTICE) {
            return;
        }
        AccessDecision decision = entitlementResolver.checkAssessmentAccess(userId, assessmentId, serverNow);
        if (!decision.allowed()) {
            throw new PaidContentAccessDeniedException();
        }
    }
}
