package com.company.exam.analytics.controller;

import com.company.exam.analytics.dto.AnalyticsDtos;
import com.company.exam.analytics.service.AnalyticsQueryService;
import com.company.exam.common.api.ApiResponse;
import com.company.exam.identity.security.IdentityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Analytics read API ({@code specs/17-P8-Analytics-V1.5.md §57, §58, §107-§109}).
 *
 * <p>Read-only. Student analytics is scoped to the authenticated student by ownership; staff reads
 * require {@code ANALYTICS_VIEW} and are organization-scoped by authorization ({@code §107, §108}).
 * No write endpoint is exposed ({@code §58}).
 */
@RestController
@RequestMapping(path = "/api/v1/analytics", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Analytics", description = "Derived analytics dashboards (read-only)")
public class AnalyticsController {

    private final AnalyticsQueryService analytics;

    public AnalyticsController(AnalyticsQueryService analytics) {
        this.analytics = analytics;
    }

    @Operation(summary = "Get my student analytics dashboard")
    @GetMapping("/student")
    public ApiResponse<AnalyticsDtos.StudentDashboard> student(
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Student analytics retrieved", analytics.studentDashboard(principal.userId()));
    }

    @Operation(summary = "Get my assessed attempts")
    @GetMapping("/student/assessments")
    public ApiResponse<List<AnalyticsDtos.StudentAssessmentItem>> studentAssessments(
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Student assessments retrieved", analytics.studentAssessments(principal.userId()));
    }

    @Operation(summary = "Get my topic performance")
    @GetMapping("/student/topics")
    public ApiResponse<List<AnalyticsDtos.StudentTopicItem>> studentTopics(
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Student topics retrieved", analytics.studentTopics(principal.userId()));
    }

    @Operation(summary = "Get assessment analytics")
    @GetMapping("/assessments/{assessmentId}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ANALYTICS_VIEW')")
    public ApiResponse<AnalyticsDtos.AssessmentDashboard> assessment(@PathVariable UUID assessmentId) {
        return ApiResponse.ok("Assessment analytics retrieved", analytics.assessmentDashboard(assessmentId));
    }

    @Operation(summary = "Get assessment section analysis")
    @GetMapping("/assessments/{assessmentId}/sections")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ANALYTICS_VIEW')")
    public ApiResponse<List<AnalyticsDtos.SectionAnalyticsView>> assessmentSections(@PathVariable UUID assessmentId) {
        return ApiResponse.ok("Assessment sections retrieved", analytics.sections(assessmentId));
    }

    @Operation(summary = "Get assessment question analytics")
    @GetMapping("/assessments/{assessmentId}/questions")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ANALYTICS_VIEW')")
    public ApiResponse<List<AnalyticsDtos.QuestionAnalyticsResponse>> assessmentQuestions(
            @PathVariable UUID assessmentId) {
        return ApiResponse.ok("Assessment questions retrieved", analytics.assessmentQuestions(assessmentId));
    }

    @Operation(summary = "Get question analytics")
    @GetMapping("/questions/{questionId}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ANALYTICS_VIEW')")
    public ApiResponse<AnalyticsDtos.QuestionAnalyticsResponse> question(@PathVariable UUID questionId) {
        return ApiResponse.ok("Question analytics retrieved", analytics.question(questionId));
    }

    @Operation(summary = "Get topic analytics")
    @GetMapping("/topics/{topicId}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ANALYTICS_VIEW')")
    public ApiResponse<AnalyticsDtos.TopicAnalyticsResponse> topic(@PathVariable UUID topicId) {
        return ApiResponse.ok("Topic analytics retrieved", analytics.topic(topicId));
    }

    @Operation(summary = "Get organization analytics")
    @GetMapping("/organization")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ANALYTICS_VIEW')")
    public ApiResponse<AnalyticsDtos.OrganizationAnalyticsResponse> organization() {
        return ApiResponse.ok("Organization analytics retrieved", analytics.organization());
    }
}
