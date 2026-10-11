package com.company.exam.content.template;

import com.company.exam.common.api.ApiError;
import com.company.exam.common.exception.BusinessException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Maps database question-template codes to backend strategy rules
 * ({@code specs/12-P3-Content-V1.5.md §121-§123}).
 *
 * <p>Data-driven rather than one giant switch. A template code with no registered rule cannot be
 * published and never reaches delivery.
 */
@Component
public class QuestionTypeRegistry {

    /** Option/answer behaviour for a template code. */
    public record QuestionTypeRule(boolean optionBased, int minOptions, int maxOptions,
            Integer exactCorrectOptions, Integer minCorrectOptions) {
    }

    private static final Map<String, QuestionTypeRule> RULES = Map.of(
            "SINGLE_CHOICE", new QuestionTypeRule(true, 2, 10, 1, null),
            "MULTIPLE_CHOICE", new QuestionTypeRule(true, 2, 10, null, 1),
            "TRUE_FALSE", new QuestionTypeRule(true, 2, 2, 1, null),
            "INTEGER", new QuestionTypeRule(false, 0, 0, null, null),
            "DECIMAL", new QuestionTypeRule(false, 0, 0, null, null),
            "FILL_IN_THE_BLANK", new QuestionTypeRule(false, 0, 0, null, null));

    public boolean isSupported(String code) {
        return code != null && RULES.containsKey(code);
    }

    /** Returns the rule or throws a controlled business error for an unknown template. */
    public QuestionTypeRule require(String code) {
        QuestionTypeRule rule = code == null ? null : RULES.get(code);
        if (rule == null) {
            throw new BusinessException("Question template is not supported: " + code,
                    List.of(ApiError.of("templateId", "UNKNOWN_TEMPLATE",
                            "No active implementation exists for this question template")));
        }
        return rule;
    }

    /**
     * Validates option count and correctness against the template rules
     * ({@code specs/12-P3-Content-V1.5.md §32-§33}).
     */
    public void validateOptions(String code, int totalOptions, int correctOptions) {
        QuestionTypeRule rule = require(code);
        if (!rule.optionBased()) {
            return;
        }
        if (totalOptions < rule.minOptions() || totalOptions > rule.maxOptions()) {
            throw new BusinessException("Template " + code + " requires between " + rule.minOptions()
                    + " and " + rule.maxOptions() + " options",
                    List.of(ApiError.of("options", "INVALID_OPTION_COUNT", "Invalid number of options")));
        }
        if (rule.exactCorrectOptions() != null && correctOptions != rule.exactCorrectOptions()) {
            throw new BusinessException("Template " + code + " requires exactly " + rule.exactCorrectOptions()
                    + " correct option(s)",
                    List.of(ApiError.of("options", "INVALID_CORRECT_OPTION_COUNT", "Invalid number of correct options")));
        }
        if (rule.minCorrectOptions() != null && correctOptions < rule.minCorrectOptions()) {
            throw new BusinessException("Template " + code + " requires at least " + rule.minCorrectOptions()
                    + " correct option(s)",
                    List.of(ApiError.of("options", "INVALID_CORRECT_OPTION_COUNT", "Not enough correct options")));
        }
    }
}
