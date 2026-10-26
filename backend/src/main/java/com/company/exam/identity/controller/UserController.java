package com.company.exam.identity.controller;

import com.company.exam.common.api.ApiResponse;
import com.company.exam.common.api.PageResponse;
import com.company.exam.common.enums.Status;
import com.company.exam.identity.dto.UserDtos;
import com.company.exam.identity.security.IdentityPrincipal;
import com.company.exam.identity.service.UserService;
import com.company.exam.common.web.QuerySupport;
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

/**
 * User management API ({@code specs/10-P1-Identity-V1.5.md §53-§59, §68};
 * {@code specs/05-API-Contract-V1.5.md §23}).
 *
 * <p>Each operation is protected by the permission from the authorization matrix.
 */
@RestController
@RequestMapping(path = "/api/v1/users", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Users", description = "Identity user management")
public class UserController {

    private static final Set<String> SORTABLE = Set.of("createdOn", "email", "firstName", "lastName", "status");

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "List users", description = "Paginated, filterable user list.")
    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'USER_VIEW')")
    public ApiResponse<List<UserDtos.UserSummary>> list(
            @RequestParam(required = false) Status status,
            @RequestParam(required = false) UUID roleId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        Page<UserDtos.UserSummary> page = userService.list(status, roleId, search,
                QuerySupport.sanitize(pageable, SORTABLE), principal.userId());
        return ApiResponse.paged(PageResponse.from(page, summary -> summary));
    }

    @Operation(summary = "Get a user", description = "Returns safe profile and role information.")
    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'USER_VIEW')")
    public ApiResponse<UserDtos.UserDetail> get(@PathVariable UUID id,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok(userService.get(id, principal.userId()));
    }

    @Operation(summary = "Create a user", description = "Creates a user and assigns roles.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'USER_CREATE')")
    public ApiResponse<UserDtos.UserDetail> create(@Valid @RequestBody UserDtos.CreateUserRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("User created", userService.create(request, principal.userId()));
    }

    @Operation(summary = "Update a user", description = "Updates permitted profile fields and role assignments.")
    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'USER_UPDATE')")
    public ApiResponse<UserDtos.UserDetail> update(@PathVariable UUID id,
            @Valid @RequestBody UserDtos.UpdateUserRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("User updated", userService.update(id, request, principal.userId()));
    }

    @Operation(summary = "Archive a user", description = "Archives (soft-deactivates) a user.")
    @DeleteMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'USER_ARCHIVE')")
    public ApiResponse<Void> archive(@PathVariable UUID id,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        userService.archive(id, principal.userId());
        return ApiResponse.ok("User archived", null);
    }
}
