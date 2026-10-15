package com.company.exam.assessment.api;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The Assessment application contract consumed by Analytics
 * ({@code specs/17-P8-Analytics-V1.5.md §12, §42, §45, §77}).
 *
 * <p>Assessment analytics has Assessment grain ({@code §42}); version-level detail is derived
 * dynamically from this read contract ({@code §77}).
 */
public interface AssessmentAnalyticsCatalog {

    /** One assessment version and its logical assessment. */
    record VersionRow(UUID versionId, UUID assessmentId, String engineType) {
    }

    /** One snapshot-relevant assessment question binding. */
    record QuestionRow(UUID versionId, UUID questionVersionId, String sectionName, BigDecimal marks) {
    }

    /** Qualifying (ACTIVE, non-draft, non-archived) logical assessment ids ({@code §42}). */
    List<UUID> qualifyingAssessmentIds();

    /** Every assessment version of the deployment. */
    List<VersionRow> allVersions();

    /** The assessment versions with the given ids. */
    List<VersionRow> versions(Collection<UUID> versionIds);

    /** The question bindings of the given assessment versions. */
    List<QuestionRow> questionsForVersions(Collection<UUID> versionIds);
}
