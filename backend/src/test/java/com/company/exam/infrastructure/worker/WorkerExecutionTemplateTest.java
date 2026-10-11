package com.company.exam.infrastructure.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.exam.common.exception.SystemException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class WorkerExecutionTemplateTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final WorkerExecutionTemplate template = new WorkerExecutionTemplate(meterRegistry);

    @Test
    void successfulTaskIsCountedAndTimed() {
        template.execute("demo", () -> {
        });

        assertThat(successCount("demo")).isEqualTo(1.0d);
        assertThat(failureCount("demo")).isZero();
        assertThat(meterRegistry.find("exam.worker.duration").tag("jobType", "demo").timer()).isNotNull();
    }

    @Test
    void failingTaskCountsFailureAndIsTranslated() {
        assertThatThrownBy(() -> template.execute("demo", () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(SystemException.class)
                .hasMessageContaining("demo")
                .hasCauseInstanceOf(IllegalStateException.class);

        assertThat(failureCount("demo")).isEqualTo(1.0d);
        assertThat(successCount("demo")).isZero();
    }

    private double successCount(String jobType) {
        return count(jobType, "success");
    }

    private double failureCount(String jobType) {
        return count(jobType, "failure");
    }

    private double count(String jobType, String outcome) {
        var counter = meterRegistry.find("exam.worker.executions")
                .tags("jobType", jobType, "outcome", outcome)
                .counter();
        return counter == null ? 0d : counter.count();
    }
}
