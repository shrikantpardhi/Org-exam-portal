package com.company.exam.communication.controller;

import com.company.exam.common.api.ApiResponse;
import com.company.exam.common.api.PageResponse;
import com.company.exam.common.web.QuerySupport;
import com.company.exam.communication.dto.CommunicationDtos;
import com.company.exam.communication.entity.NotificationStatus;
import com.company.exam.communication.service.NotificationService;
import com.company.exam.identity.security.IdentityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The notification center API ({@code specs/19-P10-Communication-V1.5.md §12, §65}).
 *
 * <p>Every endpoint is scoped to the authenticated user; there is no cross-user notification access
 * and no public notification-creation endpoint ({@code §14, §65}).
 */
@RestController
@RequestMapping(path = "/api/v1/notifications", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Communication - Notifications", description = "The student notification center")
public class NotificationController {

    private static final Set<String> SORTABLE = Set.of("createdOn", "notificationStatus");

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(summary = "List my notifications")
    @GetMapping
    public ApiResponse<java.util.List<CommunicationDtos.NotificationView>> list(
            @RequestParam(required = false) NotificationStatus status,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        PageResponse<CommunicationDtos.NotificationView> page = PageResponse.from(
                notificationService.listForUser(principal.userId(), status, QuerySupport.sanitize(pageable, SORTABLE)),
                view -> view);
        return ApiResponse.paged("Notifications retrieved", page);
    }

    @Operation(summary = "Mark one notification read")
    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable UUID id, @AuthenticationPrincipal IdentityPrincipal principal) {
        notificationService.markRead(principal.userId(), id);
        return ApiResponse.ok("Notification marked read", null);
    }

    @Operation(summary = "Mark all my notifications read")
    @PostMapping("/read-all")
    public ApiResponse<Integer> markAllRead(@AuthenticationPrincipal IdentityPrincipal principal) {
        int updated = notificationService.markAllRead(principal.userId());
        return ApiResponse.ok("Notifications marked read", updated);
    }
}
