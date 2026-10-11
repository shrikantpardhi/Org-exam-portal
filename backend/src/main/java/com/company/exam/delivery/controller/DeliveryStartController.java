package com.company.exam.delivery.controller;

import com.company.exam.common.api.ApiResponse;
import com.company.exam.common.job.BackgroundJob;
import com.company.exam.delivery.application.DeliveryDtos;
import com.company.exam.delivery.application.SnapshotPreparationService;
import com.company.exam.delivery.application.StartAssessmentService;
import com.company.exam.identity.security.IdentityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Delivery launch API ({@code specs/14-P5-Delivery-V1.5.md §28, §44.1}). The schedule-scoped
 * delivery operations live under the schedule resource, matching the frozen inventory.
 */
@RestController
@RequestMapping(path = "/api/v1/assessment-schedules", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Delivery - Launch", description = "Attempt preparation and Start Exam")
public class DeliveryStartController {

    private final SnapshotPreparationService preparationService;
    private final StartAssessmentService startAssessmentService;

    public DeliveryStartController(SnapshotPreparationService preparationService,
            StartAssessmentService startAssessmentService) {
        this.preparationService = preparationService;
        this.startAssessmentService = startAssessmentService;
    }

    @Operation(summary = "Prepare attempts for a schedule")
    @PostMapping("/{scheduleId}/prepare-attempts")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'DELIVERY_PREPARE_ATTEMPTS')")
    public ApiResponse<DeliveryDtos.PrepareResponse> prepareAttempts(@PathVariable UUID scheduleId,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        preparationService.createPendingAttemptsForSchedule(scheduleId, principal.userId());
        BackgroundJob job = preparationService.enqueuePreparationJob(scheduleId, principal.userId());
        return ApiResponse.ok("Attempt preparation queued",
                new DeliveryDtos.PrepareResponse(job.getId(), job.getStatus().name(), scheduleId));
    }

    @Operation(summary = "Start an assessment")
    @PostMapping("/{scheduleId}/start")
    public ApiResponse<DeliveryDtos.StartResponse> start(@PathVariable UUID scheduleId,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Assessment started", startAssessmentService.start(scheduleId, principal.userId()));
    }
}
