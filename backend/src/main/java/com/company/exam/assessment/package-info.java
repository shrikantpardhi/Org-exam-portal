/**
 * Assessment module boundary.
 *
 * <p>Owns Blueprint, BlueprintVersion, Assessment, AssessmentVersion, AssessmentQuestion,
 * AssessmentSchedule, AssessmentCollection, CollectionAssessment, and AssessmentSelectionRule.
 * Responsible for blueprint creation, assessment construction, rule-based materialization,
 * publishing, scheduling, collections, and engine resolution.
 *
 * <p>P0 establishes this boundary only; the module is implemented in P4.
 *
 * <p>See {@code specs/06-Architecture-Freeze-V1.5.md §34}.
 */
package com.company.exam.assessment;
