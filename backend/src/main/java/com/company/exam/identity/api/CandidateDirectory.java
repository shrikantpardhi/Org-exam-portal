package com.company.exam.identity.api;

import java.util.List;
import java.util.UUID;

/**
 * The Identity application contract used by Delivery to resolve the scheduled candidate population
 * ({@code specs/14-P5-Delivery-V1.5.md §19}).
 *
 * <p>P5 §19 states the candidate population mechanism for scheduled mass-exam preparation is a P5
 * design decision using the existing operational candidate set. Until Commerce/AccessGrant exists
 * (P9), the operational candidate set is the set of ACTIVE users holding the STUDENT role. Recorded
 * in {@code docs/known-gaps.md} (GAP-057).
 */
public interface CandidateDirectory {

    /** Returns the ids of ACTIVE users eligible to be prepared for a scheduled assessment. */
    List<UUID> activeCandidateUserIds();
}
