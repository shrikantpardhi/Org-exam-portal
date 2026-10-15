package com.company.exam.identity.api;

import java.util.List;
import java.util.UUID;

/**
 * The Identity application contract consumed by Analytics
 * ({@code specs/17-P8-Analytics-V1.5.md §12, §41, §44, §73}).
 *
 * <p>Analytics reads the qualifying student population and the organization scope here rather than
 * reaching into Identity repositories. It never sees credentials or permission internals.
 */
public interface AnalyticsIdentityCatalog {

    /** ACTIVE users holding the STUDENT role — the organization's student population ({@code §41}). */
    List<UUID> activeStudentUserIds();

    /** The organization scope of the deployment ({@code §73}). */
    List<UUID> organizationIds();
}
