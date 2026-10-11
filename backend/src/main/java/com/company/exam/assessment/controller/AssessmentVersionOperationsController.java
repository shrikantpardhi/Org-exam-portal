package com.company.exam.assessment.controller;

import com.company.exam.assessment.dto.AssessmentDtos;
import com.company.exam.assessment.service.AssessmentPublicationService;
import com.company.exam.assessment.service.AssessmentPreviewService;
import com.company.exam.assessment.service.AssessmentSelectionRuleService;
import com.company.exam.assessment.service.AssessmentValidationService;
import com.company.exam.assessment.service.AssessmentVersionService;
import com.company.exam.assessment.service.QuestionSelectionService;
import com.company.exam.common.api.ApiResponse;
import com.company.exam.identity.security.IdentityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Assessment version, question, rule, validation, preview, and publication API ({@code §66, §41, §58, §63}). */
@RestController
@RequestMapping(path = "/api/v1/assessment-versions", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Assessment - Versions", description = "Assessment versions, questions, rules, and publication")
public class AssessmentVersionOperationsController {

    private final AssessmentVersionService versionService;
    private final com.company.exam.assessment.service.AssessmentQuestionService questionService;
    private final AssessmentSelectionRuleService ruleService;
    private final QuestionSelectionService selectionService;
    private final AssessmentValidationService validationService;
    private final AssessmentPreviewService previewService;
    private final AssessmentPublicationService publicationService;

    public AssessmentVersionOperationsController(AssessmentVersionService versionService,
            com.company.exam.assessment.service.AssessmentQuestionService questionService,
            AssessmentSelectionRuleService ruleService, QuestionSelectionService selectionService,
            AssessmentValidationService validationService, AssessmentPreviewService previewService,
            AssessmentPublicationService publicationService) {
        this.versionService = versionService;
        this.questionService = questionService;
        this.ruleService = ruleService;
        this.selectionService = selectionService;
        this.validationService = validationService;
        this.previewService = previewService;
        this.publicationService = publicationService;
    }

    @Operation(summary = "List questions of a version")
    @GetMapping("/{versionId}/questions")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_VERSION_VIEW')")
    public ApiResponse<List<AssessmentDtos.AssessmentQuestionResponse>> listQuestions(@PathVariable UUID versionId) {
        return ApiResponse.ok(questionService.list(versionId));
    }

    @Operation(summary = "Add a question to a version")
    @PostMapping("/{versionId}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_QUESTION_MANAGE')")
    public ApiResponse<AssessmentDtos.AssessmentQuestionResponse> addQuestion(@PathVariable UUID versionId,
            @Valid @RequestBody AssessmentDtos.AddAssessmentQuestionRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Question added", questionService.add(versionId, request, principal.userId()));
    }

    @Operation(summary = "Update an assessment question")
    @PutMapping("/{versionId}/questions/{assessmentQuestionId}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_QUESTION_MANAGE')")
    public ApiResponse<AssessmentDtos.AssessmentQuestionResponse> updateQuestion(@PathVariable UUID versionId,
            @PathVariable UUID assessmentQuestionId,
            @Valid @RequestBody AssessmentDtos.UpdateAssessmentQuestionRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Question updated",
                questionService.update(versionId, assessmentQuestionId, request, principal.userId()));
    }

    @Operation(summary = "Remove an assessment question")
    @DeleteMapping("/{versionId}/questions/{assessmentQuestionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_QUESTION_MANAGE')")
    public void removeQuestion(@PathVariable UUID versionId, @PathVariable UUID assessmentQuestionId,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        questionService.remove(versionId, assessmentQuestionId, principal.userId());
    }

    @Operation(summary = "Reorder assessment questions")
    @PutMapping("/{versionId}/questions/order")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_QUESTION_MANAGE')")
    public ApiResponse<Void> reorder(@PathVariable UUID versionId, @Valid @RequestBody AssessmentDtos.ReorderRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        questionService.reorder(versionId, request, principal.userId());
        return ApiResponse.ok("Questions reordered", null);
    }

    @Operation(summary = "List selection rules")
    @GetMapping("/{versionId}/rules")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_VERSION_VIEW')")
    public ApiResponse<List<AssessmentDtos.SelectionRuleResponse>> listRules(@PathVariable UUID versionId) {
        return ApiResponse.ok(ruleService.list(versionId));
    }

    @Operation(summary = "Create a selection rule")
    @PostMapping("/{versionId}/rules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_SELECTION_RULE_MANAGE')")
    public ApiResponse<AssessmentDtos.SelectionRuleResponse> createRule(@PathVariable UUID versionId,
            @Valid @RequestBody AssessmentDtos.SelectionRuleCreateRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Selection rule created", ruleService.create(versionId, request, principal.userId()));
    }

    @Operation(summary = "Update a selection rule")
    @PutMapping("/{versionId}/rules/{ruleId}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_SELECTION_RULE_MANAGE')")
    public ApiResponse<AssessmentDtos.SelectionRuleResponse> updateRule(@PathVariable UUID versionId,
            @PathVariable UUID ruleId, @Valid @RequestBody AssessmentDtos.SelectionRuleUpdateRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Selection rule updated",
                ruleService.update(versionId, ruleId, request, principal.userId()));
    }

    @Operation(summary = "Delete a selection rule")
    @DeleteMapping("/{versionId}/rules/{ruleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_SELECTION_RULE_MANAGE')")
    public void deleteRule(@PathVariable UUID versionId, @PathVariable UUID ruleId,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        ruleService.delete(versionId, ruleId, principal.userId());
    }

    @Operation(summary = "Materialize selection rules")
    @PostMapping("/{versionId}/rules/materialize")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_SELECTION_RULE_MANAGE')")
    public ApiResponse<List<AssessmentDtos.AssessmentQuestionResponse>> materialize(@PathVariable UUID versionId,
            @Valid @RequestBody AssessmentDtos.MaterializeRequest request,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Rules materialized",
                selectionService.materialize(versionId, request.mode(), principal.userId()));
    }

    @Operation(summary = "Validate an assessment version")
    @PostMapping("/{versionId}/validate")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_VERSION_VALIDATE')")
    public ApiResponse<AssessmentDtos.ValidationResult> validate(@PathVariable UUID versionId) {
        return ApiResponse.ok("Assessment validation completed", validationService.validate(versionId));
    }

    @Operation(summary = "Preview an assessment version")
    @GetMapping("/{versionId}/preview")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_VERSION_VIEW')")
    public ApiResponse<AssessmentDtos.PreviewResponse> preview(@PathVariable UUID versionId) {
        return ApiResponse.ok("Assessment preview", previewService.preview(versionId));
    }

    @Operation(summary = "Publish an assessment version")
    @PostMapping("/{versionId}/publish")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_VERSION_PUBLISH')")
    public ApiResponse<AssessmentDtos.AssessmentVersionResponse> publish(@PathVariable UUID versionId,
            @AuthenticationPrincipal IdentityPrincipal principal) {
        return ApiResponse.ok("Assessment version published", publicationService.publish(versionId, principal.userId()));
    }

    @Operation(summary = "Get an assessment version")
    @GetMapping("/{versionId}")
    @PreAuthorize("@authorizationService.hasPermission(authentication, 'ASSESSMENT_VERSION_VIEW')")
    public ApiResponse<AssessmentDtos.AssessmentVersionResponse> get(@PathVariable UUID versionId) {
        return ApiResponse.ok(versionService.get(versionId));
    }
}
