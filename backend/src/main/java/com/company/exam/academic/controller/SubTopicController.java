package com.company.exam.academic.controller;

import com.company.exam.academic.dto.AcademicDtos;
import com.company.exam.academic.service.SubTopicService;
import com.company.exam.common.api.ApiResponse;
import com.company.exam.common.api.PageResponse;
import com.company.exam.common.enums.Status;
import com.company.exam.common.web.QuerySupport;
import com.company.exam.identity.security.IdentityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
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

/** SubTopic API ({@code specs/11-P2-Academic-V1.5.md §35}). Path {@code /sub-topics}. */
@RestController
@RequestMapping(path = "/api/v1/sub-topics", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Academic - SubTopics", description = "Sub-topics within a topic")
public class SubTopicController {

    private static final Set<String> SORTABLE = Set.of("code", "name", "status", "createdOn", "updatedOn");

    private final SubTopicService subTopicService;

    public SubTopicController(SubTopicService subTopicService) {
        this.subTopicService = subTopicService;
    }

    @Operation(summary = "List sub-topics of a topic (topicId required)")
    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'SUB_TOPIC_VIEW')")
    public ApiResponse<List<AcademicDtos.SubTopicListItem>> list(
            @RequestParam(required = false) UUID topicId,
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<AcademicDtos.SubTopicListItem> page =
                subTopicService.list(topicId, status, search, QuerySupport.sanitize(pageable, SORTABLE));
        return ApiResponse.paged(PageResponse.from(page, item -> item));
    }

    @Operation(summary = "Get a sub-topic")
    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'SUB_TOPIC_VIEW')")
    public ApiResponse<AcademicDtos.SubTopicResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(subTopicService.get(id));
    }

    @Operation(summary = "Create a sub-topic")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'SUB_TOPIC_CREATE')")
    public ApiResponse<AcademicDtos.SubTopicResponse> create(
            @Valid @RequestBody AcademicDtos.SubTopicCreateRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Sub-topic created", subTopicService.create(request, principal.userId()));
    }

    @Operation(summary = "Update a sub-topic")
    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'SUB_TOPIC_UPDATE')")
    public ApiResponse<AcademicDtos.SubTopicResponse> update(@PathVariable UUID id,
            @Valid @RequestBody AcademicDtos.SubTopicUpdateRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Sub-topic updated", subTopicService.update(id, request, principal.userId()));
    }

    @Operation(summary = "Archive a sub-topic")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'SUB_TOPIC_ARCHIVE')")
    public void archive(@PathVariable UUID id, @AuthenticationPrincipal IdentityPrincipal principal) {
        subTopicService.archive(id, principal.userId());
    }
}
