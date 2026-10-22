package com.company.exam.evaluation.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.exam.evaluation.entity.QuestionOutcome;
import com.company.exam.evaluation.exception.ScoringConfigurationInvalidException;
import com.company.exam.evaluation.scoring.QuestionEvaluationContext;
import com.company.exam.evaluation.scoring.QuestionEvaluationOutcome;
import com.company.exam.evaluation.scoring.QuestionOutcomeState;
import com.company.exam.evaluation.scoring.QuestionScoringPolicy;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Numeric and text objective scoring ({@code specs/15-P6-Evaluation-V1.5.md §22, §23}). */
class NumericAndTextEvaluatorTest {

    private final QuestionScoringPolicy policy = new QuestionScoringPolicy();
    private final NumericAnswerEvaluator numeric = new NumericAnswerEvaluator(policy);
    private final TextAnswerEvaluator text = new TextAnswerEvaluator(policy);
    private final UUID questionVersionId = UUID.randomUUID();

    private QuestionEvaluationContext numericContext(String response, String correct) {
        return new QuestionEvaluationContext(questionVersionId, "INTEGER", new BigDecimal("1.00"), BigDecimal.ZERO,
                response, QuestionOutcome.NORMAL, null, List.<UUID>of(), correct, null, null, Map.of());
    }

    private QuestionEvaluationContext textContext(String response, String correct) {
        return new QuestionEvaluationContext(questionVersionId, "FILL_IN_THE_BLANK", new BigDecimal("1.00"),
                BigDecimal.ZERO, response, QuestionOutcome.NORMAL, null, List.<UUID>of(), correct, null, null, Map.of());
    }

    @Test
    void numericComparisonIsScaleInsensitive() {
        QuestionEvaluationOutcome outcome = numeric.evaluate(numericContext("7", "7.00"));
        assertThat(outcome.state()).isEqualTo(QuestionOutcomeState.CORRECT);
    }

    @Test
    void aNonNumericResponseIsAnExplicitInvalidResponse() {
        QuestionEvaluationOutcome outcome = numeric.evaluate(numericContext("seven", "7"));
        assertThat(outcome.state()).isEqualTo(QuestionOutcomeState.INVALID_RESPONSE);
    }

    @Test
    void aNonNumericAuthoritativeAnswerIsAnInvalidScoringConfiguration() {
        assertThatThrownBy(() -> numeric.evaluate(numericContext("7", "seven")))
                .isInstanceOf(ScoringConfigurationInvalidException.class);
    }

    @Test
    void blankNumericResponseIsUnanswered() {
        QuestionEvaluationOutcome outcome = numeric.evaluate(numericContext("  ", "7"));
        assertThat(outcome.state()).isEqualTo(QuestionOutcomeState.UNANSWERED);
    }

    @Test
    void textComparisonIsTrimmedCaseInsensitiveAndWhitespaceCollapsed() {
        QuestionEvaluationOutcome outcome = text.evaluate(textContext("  hello   WORLD ", "Hello World"));
        assertThat(outcome.state()).isEqualTo(QuestionOutcomeState.CORRECT);
    }

    @Test
    void differingTextIsIncorrect() {
        QuestionEvaluationOutcome outcome = text.evaluate(textContext("hello", "goodbye"));
        assertThat(outcome.state()).isEqualTo(QuestionOutcomeState.INCORRECT);
    }
}
