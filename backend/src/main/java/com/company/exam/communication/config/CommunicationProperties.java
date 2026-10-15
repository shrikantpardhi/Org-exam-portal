package com.company.exam.communication.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Communication configuration ({@code specs/19-P10-Communication-V1.5.md §33, §35, §85, §192}).
 *
 * <p>Retry defaults follow the frozen values: a bounded maximum number of attempts, exponential
 * backoff starting at 60 seconds with jitter and a 30-minute cap ({@code §33, §35}).
 */
@ConfigurationProperties(prefix = "app.communication")
public class CommunicationProperties {

    /** The active {@code EmailGateway} provider id. Only a fake provider ships with the platform. */
    private String provider = "FAKE";

    /** Maximum delivery attempts including the first ({@code §33}). */
    private int maxAttempts = 5;

    /** Backoff base delay ({@code §33}: "exponential starting at 60 seconds"). */
    private Duration backoffBase = Duration.ofSeconds(60);

    /** Backoff multiplier applied per attempt. */
    private double backoffMultiplier = 5.0;

    /** Maximum delay between attempts ({@code §33}: "cap: 30 minutes"). */
    private Duration backoffCap = Duration.ofMinutes(30);

    /** Jitter applied to the delay, as a fraction (0.20 = plus or minus 20 percent) ({@code §33}). */
    private double backoffJitter = 0.20;

    /** Notification batch size for fan-out inserts ({@code §48, §49}). */
    private int fanoutBatchSize = 500;

    /** Bounded worker batch size. */
    private int workerBatchSize = 50;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration getBackoffBase() {
        return backoffBase;
    }

    public void setBackoffBase(Duration backoffBase) {
        this.backoffBase = backoffBase;
    }

    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }

    public void setBackoffMultiplier(double backoffMultiplier) {
        this.backoffMultiplier = backoffMultiplier;
    }

    public Duration getBackoffCap() {
        return backoffCap;
    }

    public void setBackoffCap(Duration backoffCap) {
        this.backoffCap = backoffCap;
    }

    public double getBackoffJitter() {
        return backoffJitter;
    }

    public void setBackoffJitter(double backoffJitter) {
        this.backoffJitter = backoffJitter;
    }

    public int getFanoutBatchSize() {
        return fanoutBatchSize;
    }

    public void setFanoutBatchSize(int fanoutBatchSize) {
        this.fanoutBatchSize = fanoutBatchSize;
    }

    public int getWorkerBatchSize() {
        return workerBatchSize;
    }

    public void setWorkerBatchSize(int workerBatchSize) {
        this.workerBatchSize = workerBatchSize;
    }
}
