package com.company.exam.communication.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The fixed template renderer ({@code specs/19-P10-Communication-V1.5.md §24, §26}).
 *
 * <p>Rendering is pure string replacement; there is no expression evaluation or scripting.
 */
class TemplateRendererTest {

    private final TemplateRenderer renderer = new TemplateRenderer();

    @Test
    void replacesKnownVariables() {
        String rendered = renderer.render("Hello {{studentName}}, welcome to {{organizationName}}.",
                Map.of("studentName", "Asha", "organizationName", "Exam Co"));
        assertThat(rendered).isEqualTo("Hello Asha, welcome to Exam Co.");
    }

    @Test
    void preservesUnknownVariablesRatherThanBlank() {
        assertThat(renderer.render("Hi {{missing}}", Map.of())).isEqualTo("Hi {{missing}}");
    }

    @Test
    void doesNotEvaluateExpressions() {
        // No expression evaluation: the placeholder is left literally, never computed to 2.
        assertThat(renderer.render("Result {{1+1}}", Map.of())).isEqualTo("Result {{1+1}}");
    }

    @Test
    void valuesAreInsertedLiterallyWithoutReRendering() {
        String rendered = renderer.render("{{studentName}}", Map.of("studentName", "{{nested}}"));
        assertThat(rendered).isEqualTo("{{nested}}");
    }

    @Test
    void supportsToleratedWhitespace() {
        assertThat(renderer.render("Hi {{  studentName  }}", Map.of("studentName", "Asha")))
                .isEqualTo("Hi Asha");
    }
}
