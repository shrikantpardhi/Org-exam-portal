package com.company.exam.assessment.controller;

import com.company.exam.assessment.dto.AssessmentDtos;
import com.company.exam.assessment.service.AssessmentScheduleService;
import com.company.exam.common.api.ApiResponse;
import com.company.exam.common.api.PageResponse;
import com.company.exam.common.enums.Status;
import com.company.exam.common.web.QuerySupport;
import com.company.exam.identity.security.IdentityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Schedule API ({@code specs/13-P4-Assessment-V1.5.md §67}). */
@RestController
@RequestMapping(path = "/api/v1/assessment-schedules", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Assessment - Schedules", description = "Assessment schedules")
public class AssessmentScheduleController {

    private static final Set<String> SORTABLE = Set.of("startTime", "endTime", "status", "createdOn");

    private final AssessmentScheduleService scheduleService;

    public AssessmentScheduleController(AssessmentScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @Operation(summary = "List schedules")
    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_SCHEDULE_VIEW')")
    public ApiResponse<List<AssessmentDtos.ScheduleResponse>> list(
            @RequestParam(required = false) UUID assessmentVersionId,
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.paged(PageResponse.from(scheduleService.list(assessmentVersionId, status, from, to,
                QuerySupport.sanitize(pageable, SORTABLE)), item -> item));
    }

    @Operation(summary = "Create a schedule")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_SCHEDULE_CREATE')")
    public ApiResponse<AssessmentDtos.ScheduleResponse> create(
            @Valid @RequestBody AssessmentDtos.ScheduleCreateRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Schedule created", scheduleService.create(request, principal.userId()));
    }

    @Operation(summary = "Update a schedule")
    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_SCHEDULE_UPDATE')")
    public ApiResponse<AssessmentDtos.ScheduleResponse> update(@PathVariable UUID id,
            @Valid @RequestBody AssessmentDtos.ScheduleUpdateRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Schedule updated", scheduleService.update(id, request, principal.userId()));
    }

    @Operation(summary = "Close a schedule")
    @PostMapping("/{id}/close")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_SCHEDULE_CLOSE')")
    public ApiResponse<AssessmentDtos.ScheduleResponse> close(@PathVariable UUID id,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Schedule closed", scheduleService.close(id, principal.userId()));
    }
}
