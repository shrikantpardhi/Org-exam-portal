package com.company.exam.analytics.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Analytics configuration ({@code specs/17-P8-Analytics-V1.5.md §20, §44, §70, §85, §91, §116}).
 *
 * <p>Thresholds are explicit and configurable rather than hard-coded, and they carry a traceable
 * analytics calculation version ({@code §91}).
 */
@ConfigurationProperties(prefix = "app.analytics")
public class AnalyticsProperties {

    /** The active-user window; a user is active with an attempt started within it ({@code §44}). */
    private Duration activeUserWindow = Duration.ofDays(30);

    /** An aggregate older than this is reported as STALE to the UI ({@code §69, §70}). */
    private Duration staleAfter = Duration.ofMinutes(15);

    /** Answers beyond this persisted time are treated as impossible outliers and excluded ({@code §116}). */
    private long averageTimeUpperBoundSeconds = 24 * 60 * 60L;

    /** Below this accuracy a topic is a weak area in read models ({@code §20, §85}). */
    private double weakAreaAccuracyThreshold = 50.0;

    /** Minimum evaluated occurrences before a topic metric is presented as robust ({@code §86}). */
    private int minimumSampleSize = 5;

    /** A traceable analytics calculation version ({@code §91}). */
    private String calculationVersion = "p8-1";

    public Duration getActiveUserWindow() {
        return activeUserWindow;
    }

    public void setActiveUserWindow(Duration activeUserWindow) {
        this.activeUserWindow = activeUserWindow;
    }

    public Duration getStaleAfter() {
        return staleAfter;
    }

    public void setStaleAfter(Duration staleAfter) {
        this.staleAfter = staleAfter;
    }

    public long getAverageTimeUpperBoundSeconds() {
        return averageTimeUpperBoundSeconds;
    }

    public void setAverageTimeUpperBoundSeconds(long averageTimeUpperBoundSeconds) {
        this.averageTimeUpperBoundSeconds = averageTimeUpperBoundSeconds;
    }

    public double getWeakAreaAccuracyThreshold() {
        return weakAreaAccuracyThreshold;
    }

    public void setWeakAreaAccuracyThreshold(double weakAreaAccuracyThreshold) {
        this.weakAreaAccuracyThreshold = weakAreaAccuracyThreshold;
    }

    public int getMinimumSampleSize() {
        return minimumSampleSize;
    }

    public void setMinimumSampleSize(int minimumSampleSize) {
        this.minimumSampleSize = minimumSampleSize;
    }

    public String getCalculationVersion() {
        return calculationVersion;
    }

    public void setCalculationVersion(String calculationVersion) {
        this.calculationVersion = calculationVersion;
    }
}
