-- ---------------------------------------------------------------------------
-- P7 post-run reconciliation (specs/16-P7-Pilot-V1.5.md §49-§54, §93-§98, §162)
--
-- READ-ONLY. No statement mutates data (AGENTS.md / P7 §152 guardrail "no manual
-- production data mutation"). Run against the load-test/staging database after each run.
--
-- Usage (psql):
--   psql "$P7_DATABASE_URL" -v schedule_id="'<uuid>'" -v assessment_version_id="'<uuid>'" \
--        -v expected_questions=100 -v expected_options=4 -f scripts/p7/reconcile.sql
--
-- Every query returns a `verdict` column: PASS when the expectation holds, FAIL otherwise.
-- A latency-pass run with a FAIL verdict is a failed test (§49).
-- ---------------------------------------------------------------------------

\set ON_ERROR_STOP on

-- 1. Count reconciliation (§94). Every started attempt should be submitted/evaluated/published
--    for a completed exam; examine the gaps rather than assuming success.
SELECT 'counts' AS gate,
       (SELECT count(*) FROM "user" u
          JOIN user_role ur ON ur.user_id = u.id
          JOIN role r ON r.id = ur.role_id
         WHERE r.code = 'STUDENT' AND u.status = 'ACTIVE' AND ur.status = 'ACTIVE') AS eligible,
       (SELECT count(*) FROM student_attempt WHERE assessment_schedule_id = :'schedule_id') AS prepared,
       (SELECT count(*) FROM student_attempt WHERE assessment_schedule_id = :'schedule_id'
          AND attempt_status IN ('IN_PROGRESS','SUBMITTED','EVALUATED')) AS started,
       (SELECT count(*) FROM student_attempt WHERE assessment_schedule_id = :'schedule_id'
          AND attempt_status IN ('SUBMITTED','EVALUATED')) AS submitted,
       (SELECT count(DISTINCT e.attempt_id) FROM evaluation e
          JOIN student_attempt a ON a.id = e.attempt_id
         WHERE a.assessment_schedule_id = :'schedule_id' AND e.superseded_by_id IS NULL) AS evaluated,
       (SELECT count(*) FROM result r
          JOIN evaluation e ON e.id = r.evaluation_id
          JOIN student_attempt a ON a.id = e.attempt_id
         WHERE a.assessment_schedule_id = :'schedule_id' AND r.result_status = 'PUBLISHED') AS published;

-- 2. Start-burst snapshot gate (§27, §50). Start Exam must insert ZERO snapshot rows, so every
--    prepared attempt has exactly the expected question count and every question the expected
--    option count. A non-zero "bad" count means Start (or a late worker) wrote snapshot rows.
SELECT 'start_burst_zero_new_rows' AS gate,
       count(*) AS bad,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS verdict
  FROM student_attempt a
 WHERE a.assessment_schedule_id = :'schedule_id'
   AND (SELECT count(*) FROM attempt_question q WHERE q.attempt_id = a.id) <> :expected_questions;

SELECT 'snapshot_option_cardinality' AS gate,
       count(*) AS bad,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS verdict
  FROM attempt_question q
  JOIN student_attempt a ON a.id = q.attempt_id
 WHERE a.assessment_schedule_id = :'schedule_id'
   AND (SELECT count(*) FROM attempt_question_option o WHERE o.attempt_question_id = q.id) <> :expected_options;

-- 3. Snapshot integrity (§50): distinct display order and exact QuestionVersion membership.
SELECT 'snapshot_duplicate_questions' AS gate, count(*) AS bad,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS verdict
  FROM (SELECT attempt_id, question_version_id FROM attempt_question
         GROUP BY attempt_id, question_version_id HAVING count(*) > 1) d;

SELECT 'snapshot_duplicate_options' AS gate, count(*) AS bad,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS verdict
  FROM (SELECT attempt_question_id, question_option_id FROM attempt_question_option
         GROUP BY attempt_question_id, question_option_id HAVING count(*) > 1) d;

SELECT 'snapshot_question_not_in_assessment' AS gate, count(*) AS bad,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS verdict
  FROM attempt_question q
  JOIN student_attempt a ON a.id = q.attempt_id
 WHERE a.assessment_schedule_id = :'schedule_id'
   AND NOT EXISTS (SELECT 1 FROM assessment_question aq
                    WHERE aq.assessment_version_id = a.assessment_version_id
                      AND aq.question_version_id = q.question_version_id);

-- 4. Response integrity (§51): one logical response per assigned question; none unassigned.
SELECT 'response_duplicates' AS gate, count(*) AS bad,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS verdict
  FROM (SELECT attempt_id, question_version_id FROM student_response
         GROUP BY attempt_id, question_version_id HAVING count(*) > 1) d;

SELECT 'response_unassigned_question' AS gate, count(*) AS bad,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS verdict
  FROM student_response r
 WHERE NOT EXISTS (SELECT 1 FROM attempt_question q
                    WHERE q.attempt_id = r.attempt_id
                      AND q.question_version_id = r.question_version_id);

-- 5. Evaluation integrity (§52): at most one current evaluation per attempt; only submitted.
SELECT 'evaluation_duplicate_current' AS gate, count(*) AS bad,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS verdict
  FROM (SELECT attempt_id FROM evaluation WHERE superseded_by_id IS NULL
         GROUP BY attempt_id HAVING count(*) > 1) d;

SELECT 'evaluation_of_unsubmitted_attempt' AS gate, count(*) AS bad,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS verdict
  FROM evaluation e JOIN student_attempt a ON a.id = e.attempt_id
 WHERE a.attempt_status NOT IN ('SUBMITTED','EVALUATED');

-- 6. Result integrity (§53): one result per evaluation; published results carry marks and rank.
SELECT 'result_duplicate_per_evaluation' AS gate, count(*) AS bad,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS verdict
  FROM (SELECT evaluation_id FROM result GROUP BY evaluation_id HAVING count(*) > 1) d;

SELECT 'published_result_missing_fields' AS gate, count(*) AS bad,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS verdict
  FROM result
 WHERE result_status = 'PUBLISHED'
   AND (obtained_marks IS NULL OR percentage IS NULL OR rank IS NULL OR percentile IS NULL);

SELECT 'rank_percentile_bounds' AS gate, count(*) AS bad,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS verdict
  FROM result
 WHERE result_status = 'PUBLISHED'
   AND (rank < 1 OR percentile < 0 OR percentile > 100);

-- 7. Scorecard reconciliation (§98): scorecard failure is distinct from result failure.
SELECT 'scorecard_duplicates' AS gate, count(*) AS bad,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS verdict
  FROM (SELECT result_id FROM scorecard GROUP BY result_id HAVING count(*) > 1) d;

-- 8. Outlier detection (§97): negative/zero/maximum anomalies and missing results. These are
--    flags for inspection, not proof of fraud.
SELECT 'result_outliers' AS gate, count(*) AS bad,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'REVIEW' END AS verdict
  FROM result
 WHERE percentage < 0 OR percentage > 100;

SELECT 'submitted_without_result' AS gate, count(*) AS bad,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'REVIEW' END AS verdict
  FROM student_attempt a
 WHERE a.assessment_schedule_id = :'schedule_id'
   AND a.attempt_status IN ('SUBMITTED','EVALUATED')
   AND NOT EXISTS (SELECT 1 FROM evaluation e
                    JOIN result r ON r.evaluation_id = e.id
                   WHERE e.attempt_id = a.id AND e.superseded_by_id IS NULL);
