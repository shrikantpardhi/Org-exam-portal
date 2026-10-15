package com.company.exam.evaluation.api;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The Evaluation application contract consumed by Analytics
 * ({@code specs/17-P8-Analytics-V1.5.md §12, §13, §25, §31, §96}).
 *
 * <p>Official score and per-question correctness come from Evaluation and {@code answer_key_detail}
 * truth ({@code §13, §31}). Analytics is not a scoring engine ({@code §1966}); the per-question
 * outcomes exposed here are produced by the <em>existing</em> P6 evaluator over the authoritative
 * answer key, not by a second implementation.
 */
public interface AnalyticsOutcomeCatalog {

    /** The current official score of an attempt. */
    record ScoreRow(UUID attemptId, BigDecimal obtainedMarks, BigDecimal percentage, String resultStatus) {
    }

    /** One authoritative per-question outcome of an attempt. */
    record QuestionOutcomeView(UUID attemptId, UUID questionVersionId, String state, BigDecimal awardedMarks) {
    }

    /** The current score of each attempt that has a current Result on record ({@code §16}). */
    List<ScoreRow> currentScores(Collection<UUID> attemptIds);

    /** Authoritative per-question outcomes for the given attempts (empty for unevaluated attempts). */
    List<QuestionOutcomeView> questionOutcomes(Collection<UUID> attemptIds);
}
