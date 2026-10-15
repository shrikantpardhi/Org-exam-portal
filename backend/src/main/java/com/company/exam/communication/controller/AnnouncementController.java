package com.company.exam.communication.controller;

import com.company.exam.common.api.ApiResponse;
import com.company.exam.common.enums.Status;
import com.company.exam.communication.dto.CommunicationDtos;
import com.company.exam.communication.entity.Announcement;
import com.company.exam.communication.service.AnnouncementService;
import com.company.exam.identity.security.IdentityPrincipal;
import com.company.exam.identity.service.AuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Announcement API ({@code specs/19-P10-Communication-V1.5.md §42, §67, §68}).
 *
 * <p>Administrators with {@code ANNOUNCEMENT_READ} see every announcement; any other authenticated
 * caller sees only the student-visible rows (ACTIVE, within the inclusive date window).
 */
@RestController
@RequestMapping(path = "/api/v1/announcements", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Communication - Announcements", description = "Organization-level announcements")
public class AnnouncementController {

    private final AnnouncementService announcementService;
    private final AuthorizationService authorizationService;

    public AnnouncementController(AnnouncementService announcementService,
            AuthorizationService authorizationService) {
        this.announcementService = announcementService;
        this.authorizationService = authorizationService;
    }

    @Operation(summary = "List announcements")
    @GetMapping
    public ApiResponse<List<CommunicationDtos.AnnouncementResponse>> list(
            @RequestParam(required = false) Status status, @PageableDefault(size = 50) Pageable pageable,
            Authentication authentication) {
        boolean staff = authorizationService.hasPermission(authentication, "ANNOUNCEMENT_READ");
        if (!staff) {
            return ApiResponse.ok("Announcements retrieved", announcementService.listVisible());
        }
        return ApiResponse.ok("Announcements retrieved", announcementService.listAdmin(status, pageable).stream()
                .map(this::toResponse)
                .toList());
    }

    @Operation(summary = "Get an announcement")
    @GetMapping("/{id}")
    public ApiResponse<CommunicationDtos.AnnouncementResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(announcementService.get(id));
    }

    @Operation(summary = "Create a draft announcement")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ANNOUNCEMENT_WRITE')")
    public ApiResponse<CommunicationDtos.AnnouncementResponse> create(
            @Valid @RequestBody CommunicationDtos.CreateAnnouncementRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Announcement created", announcementService.create(request, principal.userId()));
    }

    @Operation(summary = "Update an announcement")
    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ANNOUNCEMENT_WRITE')")
    public ApiResponse<CommunicationDtos.AnnouncementResponse> update(@PathVariable UUID id,
            @Valid @RequestBody CommunicationDtos.UpdateAnnouncementRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Announcement updated", announcementService.update(id, request, principal.userId()));
    }

    @Operation(summary = "Archive an announcement")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ANNOUNCEMENT_DELETE')")
    public void archive(@PathVariable UUID id, @AuthenticationPrincipal IdentityPrincipal principal) {
        announcementService.archive(id, principal.userId());
    }

    @Operation(summary = "Publish an announcement (queues asynchronous fan-out)")
    @PostMapping("/{id}/publish")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ANNOUNCEMENT_PUBLISH')")
    public ApiResponse<CommunicationDtos.PublishAnnouncementResponse> publish(@PathVariable UUID id,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Announcement published",
                announcementService.publish(id, principal.userId()));
    }

    private CommunicationDtos.AnnouncementResponse toResponse(Announcement announcement) {
        return new CommunicationDtos.AnnouncementResponse(announcement.getId(), announcement.getTitle(),
                announcement.getDescription(), announcement.getStartDate(), announcement.getEndDate(),
                announcement.getStatus() == null ? null : announcement.getStatus().name());
    }
}
