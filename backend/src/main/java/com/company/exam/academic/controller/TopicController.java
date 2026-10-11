package com.company.exam.academic.controller;

import com.company.exam.academic.dto.AcademicDtos;
import com.company.exam.academic.service.TopicService;
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

/** Topic API ({@code specs/11-P2-Academic-V1.5.md §34}). Required filter: {@code chapterId}. */
@RestController
@RequestMapping(path = "/api/v1/topics", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Academic - Topics", description = "Topics within a chapter")
public class TopicController {

    private static final Set<String> SORTABLE = Set.of("code", "name", "status", "createdOn", "updatedOn");

    private final TopicService topicService;

    public TopicController(TopicService topicService) {
        this.topicService = topicService;
    }

    @Operation(summary = "List topics of a chapter (chapterId required)")
    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'TOPIC_VIEW')")
    public ApiResponse<List<AcademicDtos.TopicListItem>> list(
            @RequestParam(required = false) UUID chapterId,
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<AcademicDtos.TopicListItem> page =
                topicService.list(chapterId, status, search, QuerySupport.sanitize(pageable, SORTABLE));
        return ApiResponse.paged(PageResponse.from(page, item -> item));
    }

    @Operation(summary = "Get a topic")
    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'TOPIC_VIEW')")
    public ApiResponse<AcademicDtos.TopicResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(topicService.get(id));
    }

    @Operation(summary = "Create a topic")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'TOPIC_CREATE')")
    public ApiResponse<AcademicDtos.TopicResponse> create(
            @Valid @RequestBody AcademicDtos.TopicCreateRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Topic created", topicService.create(request, principal.userId()));
    }

    @Operation(summary = "Update a topic")
    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'TOPIC_UPDATE')")
    public ApiResponse<AcademicDtos.TopicResponse> update(@PathVariable UUID id,
            @Valid @RequestBody AcademicDtos.TopicUpdateRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Topic updated", topicService.update(id, request, principal.userId()));
    }

    @Operation(summary = "Archive a topic")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'TOPIC_ARCHIVE')")
    public void archive(@PathVariable UUID id, @AuthenticationPrincipal IdentityPrincipal principal) {
        topicService.archive(id, principal.userId());
    }
}
