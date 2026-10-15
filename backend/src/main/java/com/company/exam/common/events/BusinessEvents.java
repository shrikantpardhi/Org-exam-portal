package com.company.exam.common.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * In-process business events published by producing modules and consumed by Communication
 * ({@code specs/19-P10-Communication-V1.5.md §16, §17, §52-§55, §113-§117}).
 *
 * <p>The producer owns business truth; Communication only consumes the normalized event and never
 * changes payment, access, assessment, or result state ({@code §18}). Events carry no secrets, no
 * provider payloads, and no tokens. They are published inside the business transaction and handled
 * after commit, so delivery never participates in the business transaction.
 *
 * <p>This is a shared, versionable catalog; it is not stored in any table.
 */
public final class BusinessEvents {

    private BusinessEvents() {
    }

    /** A marker for the catalog. */
    public interface BusinessEvent {
    }

    // ---------------------------------------------------------------- Commerce

    public record PaymentSucceededEvent(UUID purchaseId, UUID userId, BigDecimal amount, String currency)
            implements BusinessEvent {
    }

    public record PaymentFailedEvent(UUID purchaseId, UUID userId) implements BusinessEvent {
    }

    public record PaymentRefundedEvent(UUID purchaseId, UUID userId) implements BusinessEvent {
    }

    public record AccessGrantedEvent(UUID userId, UUID accessPackageId, Instant startDate, Instant endDate)
            implements BusinessEvent {
    }

    // ---------------------------------------------------------------- Evaluation

    public record ResultPublishedEvent(UUID attemptId, UUID userId, UUID assessmentId) implements BusinessEvent {
    }

    // ---------------------------------------------------------------- Assessment

    public record AssessmentScheduledEvent(UUID scheduleId, UUID assessmentVersionId, Instant startTime)
            implements BusinessEvent {
    }
}
