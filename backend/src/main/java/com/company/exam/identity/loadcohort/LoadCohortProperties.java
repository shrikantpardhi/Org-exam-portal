package com.company.exam.identity.loadcohort;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the synthetic load-cohort provisioning mechanism
 * ({@code specs/10-P1-Identity-V1.5.md §56}, {@code specs/16-P7-Pilot-V1.5.md §24}).
 *
 * <p>This mechanism exists because the production onboarding path deliberately cannot issue a usable
 * credential: {@code POST /api/v1/users} assigns a random secret and the account is activated through
 * password reset (§56, §58), and reset delivery is not built (GAP-002). A synthetic load cohort has no
 * mailbox, so it can never complete that flow. GAP-085 records that gap.
 *
 * <p><b>It is off by default and refuses to run in production.</b> Two independent switches must both
 * allow it: the {@code app.load-cohort.enabled} property and a non-production
 * {@code app.environment}. The property defaults to {@code false}, so a deployment that does not
 * explicitly opt in has no provisioning path at all. The environment check is enforced in
 * {@link LoadCohortProvisioningService} rather than only here, so enabling the property in production
 * still fails closed.
 */
@ConfigurationProperties(prefix = "app.load-cohort")
public class LoadCohortProperties {

    /**
     * Whether the provisioning bean exists at all. Defaults to {@code false}: an operator must opt in
     * explicitly, and the environment gate is checked again at call time.
     */
    private boolean enabled = false;

    /**
     * The environment value that must <em>never</em> permit provisioning. Compared case-insensitively
     * against {@code app.environment}.
     */
    private String forbiddenEnvironment = "production";

    /**
     * Maximum number of accounts one invocation may provision. Bounded so a mistyped argument cannot
     * create an unbounded number of accounts in a single call.
     */
    private int maxCohortSize = 20_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getForbiddenEnvironment() {
        return forbiddenEnvironment;
    }

    public void setForbiddenEnvironment(String forbiddenEnvironment) {
        this.forbiddenEnvironment = forbiddenEnvironment;
    }

    public int getMaxCohortSize() {
        return maxCohortSize;
    }

    public void setMaxCohortSize(int maxCohortSize) {
        this.maxCohortSize = maxCohortSize;
    }
}
