package com.company.exam.delivery.application;

import com.company.exam.delivery.engine.DeliveryConfiguration;
import com.company.exam.delivery.entity.StudentAttempt;
import java.time.Instant;

/** Server-authoritative timer derivation ({@code specs/14-P5-Delivery-V1.5.md §54, §57, §92}). */
final class DeliveryTimers {

    private DeliveryTimers() {
    }

    /**
     * The global timer is fully expressible from {@code started_on} plus the effective duration:
     * {@code deadline = startedOn + durationSeconds}. The browser clock is never authoritative.
     */
    static DeliveryDtos.TimerView timer(StudentAttempt attempt, DeliveryConfiguration configuration) {
        Integer durationSeconds = configuration.timer().durationSeconds();
        Instant startedOn = attempt.getStartedOn();
        Instant deadline = startedOn != null && durationSeconds != null
                ? startedOn.plusSeconds(durationSeconds)
                : null;
        return new DeliveryDtos.TimerView(configuration.timer().mode(), durationSeconds, deadline);
    }
}
