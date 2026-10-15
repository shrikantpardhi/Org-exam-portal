package com.company.exam.communication.controller;

import com.company.exam.common.api.ApiResponse;
import com.company.exam.common.enums.Status;
import com.company.exam.communication.dto.CommunicationDtos;
import com.company.exam.communication.service.EmailTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
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

/** Email template administration ({@code specs/19-P10-Communication-V1.5.md §66}). */
@RestController
@RequestMapping(path = "/api/v1/email-templates", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Communication - Email templates", description = "Reusable email content")
public class EmailTemplateController {

    private final EmailTemplateService templateService;

    public EmailTemplateController(EmailTemplateService templateService) {
        this.templateService = templateService;
    }

    @Operation(summary = "List email templates")
    @GetMapping
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'COMMUNICATION_TEMPLATE_READ')")
    public ApiResponse<List<CommunicationDtos.EmailTemplateResponse>> list(
            @RequestParam(required = false) Status status, @RequestParam(required = false) String search) {
        return ApiResponse.ok(templateService.list(status, search));
    }

    @Operation(summary = "Get an email template")
    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'COMMUNICATION_TEMPLATE_READ')")
    public ApiResponse<CommunicationDtos.EmailTemplateResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(templateService.get(id));
    }

    @Operation(summary = "Create an email template")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'COMMUNICATION_TEMPLATE_WRITE')")
    public ApiResponse<CommunicationDtos.EmailTemplateResponse> create(
            @Valid @RequestBody CommunicationDtos.CreateEmailTemplateRequest request) {
        return ApiResponse.ok("Email template created", templateService.create(request));
    }

    @Operation(summary = "Update an email template")
    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'COMMUNICATION_TEMPLATE_WRITE')")
    public ApiResponse<CommunicationDtos.EmailTemplateResponse> update(@PathVariable UUID id,
            @Valid @RequestBody CommunicationDtos.UpdateEmailTemplateRequest request) {
        return ApiResponse.ok("Email template updated", templateService.update(id, request));
    }

    @Operation(summary = "Archive an email template")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'COMMUNICATION_TEMPLATE_DELETE')")
    public void archive(@PathVariable UUID id) {
        templateService.archive(id);
    }
}
