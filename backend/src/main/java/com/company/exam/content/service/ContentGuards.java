package com.company.exam.content.service;

import com.company.exam.common.exception.InvalidStateException;
import com.company.exam.content.entity.QuestionVersion;
import org.springframework.stereotype.Component;

/**
 * Shared Content state guards ({@code specs/12-P3-Content-V1.5.md §9, §67}).
 *
 * <p>Mutations that would change a published QuestionVersion are rejected; corrections require a
 * new version.
 */
@Component
public class ContentGuards {

    public void requireMutable(QuestionVersion version) {
        if (version.isPublished()) {
            throw new InvalidStateException(
                    "Published question versions are immutable; create a new version to make corrections");
        }
    }
}
