package com.company.exam.academic.api;

import com.company.exam.common.enums.Status;
import java.util.UUID;

/**
 * The explicit Academic application contract consumed by other modules
 * ({@code specs/12-P3-Content-V1.5.md §81, §82}).
 *
 * <p>Content verifies taxonomy and master references through this contract rather than reaching into
 * Academic repositories directly, preserving the modular-monolith boundary.
 */
public interface AcademicCatalog {

    AcademicRef requireExam(UUID id);

    AcademicRef requireSubject(UUID id);

    AcademicRef requireChapter(UUID id);

    AcademicRef requireTopic(UUID id);

    AcademicRef requireSubTopic(UUID id);

    AcademicRef requireLanguage(UUID id);

    AcademicRef requireTag(UUID id);

    /** A resolved reference to an Academic master record. */
    record AcademicRef(UUID id, String label, Status status) {
    }
}
