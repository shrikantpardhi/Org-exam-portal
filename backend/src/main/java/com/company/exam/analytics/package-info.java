/**
 * Analytics module boundary.
 *
 * <p>Owns StudentAnalytics, AssessmentAnalytics, QuestionAnalytics, TopicAnalytics, and
 * OrganizationAnalytics. Responsible for aggregation, metrics, and derived data. Analytics is
 * recalculable and never modifies transactional source data.
 *
 * <p>P0 establishes this boundary only; the module is implemented in P8.
 *
 * <p>See {@code specs/06-Architecture-Freeze-V1.5.md §37}.
 */
package com.company.exam.analytics;
