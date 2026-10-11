package com.company.exam.content.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.exam.content.entity.QuestionOption;
import com.company.exam.content.entity.QuestionVersion;
import com.company.exam.content.rendering.ContentSanitizer;
import com.company.exam.content.rendering.MarkdownLatexRenderer;
import com.company.exam.content.template.QuestionTemplateConfigurationParser;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Correct-answer leakage contract ({@code specs/12-P3-Content-V1.5.md §127};
 * {@code specs/08-Exam-Engine-and-CDUI-Contract-V1.5.md §70}).
 *
 * <p>Proves that a student-facing payload built from entities that DO carry correctness never
 * serializes {@code isCorrect}, a correct option id, or an answer key.
 */
class ContentLeakageTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ContentMapper mapper = new ContentMapper(
            new MarkdownLatexRenderer(new ContentSanitizer()),
            new QuestionTemplateConfigurationParser(new ObjectMapper()));

    @Test
    void studentViewNeverSerializesCorrectness() {
        QuestionVersion version = new QuestionVersion();
        version.setId(UUID.randomUUID());
        version.setContent("What is \\(2+2\\)?");
        version.setMarks(new BigDecimal("2.00"));

        QuestionOption correct = new QuestionOption();
        correct.setId(UUID.randomUUID());
        correct.setOptionKey("A");
        correct.setDisplayOrder(1);
        correct.setContent("4");
        correct.setCorrect(true);

        QuestionOption wrong = new QuestionOption();
        wrong.setId(UUID.randomUUID());
        wrong.setOptionKey("B");
        wrong.setDisplayOrder(2);
        wrong.setContent("5");
        wrong.setCorrect(false);

        var view = mapper.toStudentView(version, List.of(correct, wrong), true);
        String json = objectMapper.writeValueAsString(view);

        assertThat(json).doesNotContain("isCorrect");
        assertThat(json).doesNotContain("correctOptionId");
        assertThat(json).doesNotContain("answerKey");
        assertThat(json).doesNotContain("correct");
        assertThat(json).contains("contentFormat").contains("MARKDOWN_LATEX_V1");
    }
}
