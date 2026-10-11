package com.company.exam.config;

import com.company.exam.infrastructure.worker.WorkerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Scheduling foundation for background work.
 *
 * <p>Provides a bounded scheduler with a controlled shutdown so that worker infrastructure does
 * not terminate mid-operation ({@code specs/09-P0-Foundation-V1.5.md §120}). Business workers are
 * added by later phases.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    @Bean
    public ThreadPoolTaskScheduler taskScheduler(WorkerProperties properties) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(properties.getSchedulerPoolSize());
        scheduler.setThreadNamePrefix("exam-worker-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(properties.getShutdownAwaitSeconds());
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
