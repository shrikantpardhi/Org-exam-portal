package com.company.exam.identity.controller;

import com.company.exam.common.api.ApiResponse;
import com.company.exam.common.api.PageResponse;
import com.company.exam.common.exception.AuthorizationException;
import com.company.exam.common.exception.NotFoundException;
import com.company.exam.identity.dto.SessionDtos;
import com.company.exam.identity.entity.LoginSession;
import com.company.exam.identity.entity.SessionStatus;
import com.company.exam.identity.mapper.IdentityMapper;
import com.company.exam.identity.security.IdentityPrincipal;
import com.company.exam.identity.service.AuthorizationService;
import com.company.exam.identity.service.SessionService;
import com.company.exam.common.web.QuerySupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login session API ({@code specs/10-P1-Identity-V1.5.md §44, §45};
 * {@code specs/05-API-Contract-V1.5.md §26}).
 *
 * <p>An authenticated user may see their own sessions; broader visibility requires
 * {@code SESSION_VIEW}. Tokens and secrets are never returned.
 */
@RestController
@RequestMapping(path = "/api/v1/login-sessions", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Login sessions", description = "Login session records")
public class LoginSessionController {

    private static final Set<String> SORTABLE = Set.of("loginTime", "sessionStatus", "userId");
    private static final String SESSION_VIEW = "SESSION_VIEW";

    private final SessionService sessionService;
    private final AuthorizationService authorizationService;
    private final IdentityMapper mapper;

    public LoginSessionController(SessionService sessionService, AuthorizationService authorizationService,
            IdentityMapper mapper) {
        this.sessionService = sessionService;
        this.authorizationService = authorizationService;
        this.mapper = mapper;
    }

    @Operation(summary = "List login sessions", description = "Filterable by userId, sessionStatus, from, and to.")
    @GetMapping
    public ApiResponse<List<SessionDtos.LoginSessionSummary>> list(
            @AuthenticationPrincipal IdentityPrincipal principal,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) SessionStatus sessionStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20) Pageable pageable) {
        boolean canViewAll = authorizationService.hasPermission(principal.userId(), SESSION_VIEW);
        UUID effectiveUserId = canViewAll ? userId : principal.userId();
        Page<LoginSession> page = sessionService.search(effectiveUserId, sessionStatus, from, to,
                QuerySupport.sanitize(pageable, SORTABLE));
        return ApiResponse.paged(PageResponse.from(page, mapper::toSummary));
    }

    @Operation(summary = "Get a login session")
    @GetMapping("/{id}")
    public ApiResponse<SessionDtos.LoginSessionDetail> get(@AuthenticationPrincipal IdentityPrincipal principal,
            @PathVariable UUID id) {
        LoginSession session = sessionService.find(id)
                .orElseThrow(() -> NotFoundException.of("Login session", id));
        boolean canViewAll = authorizationService.hasPermission(principal.userId(), SESSION_VIEW);
        if (!canViewAll && !session.getUserId().equals(principal.userId())) {
            throw new AuthorizationException("Access denied");
        }
        return ApiResponse.ok("Login session retrieved", mapper.toDetail(session));
    }
}
