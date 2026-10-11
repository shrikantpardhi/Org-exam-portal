/**
 * Evaluation module boundary.
 *
 * <p>Owns AnswerKey, AnswerKeyDetail, Evaluation, Result, and Scorecard. Responsible for scoring,
 * answer-key detail management, evaluation versioning, ranking, percentile, bonus, grace marks,
 * re-evaluation, result publication, and scorecard generation.
 *
 * <p>Evaluation reads correctness only from {@code answer_key_detail}. P0 establishes this
 * boundary only; the module is implemented in P6.
 *
 * <p>See {@code specs/06-Architecture-Freeze-V1.5.md §36}.
 */
package com.company.exam.evaluation;
