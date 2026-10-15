package com.company.exam.evaluation.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Answer-key leakage guard ({@code specs/15-P6-Evaluation-V1.5.md §87, §112, §116, §140}).
 *
 * <p>The student-facing projections must never carry answer-key mapping, correctness, or
 * question-option correctness fields.
 */
class EvaluationLeakageTest {

    private static final List<String> FORBIDDEN = List.of("correctoptionid", "correctanswertext", "answercode",
            "iscorrect", "answerkeyid", "answerkeydetail", "questionoutcome", "awardedmarks", "awardednegativemarks");

    @Test
    void studentResultProjectionCarriesNoAnswerKeyData() {
        List<String> components = Arrays.stream(EvaluationDtos.ResultResponse.class.getRecordComponents())
                .map(component -> component.getName().toLowerCase(Locale.ROOT))
                .toList();
        assertThat(components).doesNotContainAnyElementsOf(FORBIDDEN);
    }

    @Test
    void studentScorecardProjectionCarriesNoStoragePath() {
        List<String> components = Arrays.stream(EvaluationDtos.ScorecardResponse.class.getRecordComponents())
                .map(component -> component.getName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());
        assertThat(components).doesNotContain("filepath", "path", "storagepath");
    }

    @Test
    void answerKeyDetailProjectionIsStaffOnlyButStillExposesTheMappingToAllowAuthoring() {
        // The staff projection intentionally exposes the mapping; it must never be returned by a
        // student endpoint (verified by the security integration test).
        List<String> components = Arrays.stream(EvaluationDtos.AnswerKeyDetailResponse.class.getRecordComponents())
                .map(component -> component.getName().toLowerCase(Locale.ROOT))
                .toList();
        assertThat(components).contains("correctoptionid");
        assertThat(components).contains("correctanswertext");
    }
}
