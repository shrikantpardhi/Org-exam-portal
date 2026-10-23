package com.company.exam.content.template;

import com.company.exam.content.entity.QuestionTemplate;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Parses {@code question_template.configuration} JSONB into a typed structure
 * ({@code specs/12-P3-Content-V1.5.md §17, §124}).
 *
 * <p>Raw JSON lookups are not scattered across services.
 */
@Component
public class QuestionTemplateConfigurationParser {

    private final ObjectMapper objectMapper;

    public QuestionTemplateConfigurationParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Parsed, typed template configuration. */
    public record QuestionTemplateConfiguration(boolean requiresLatexParsing, Map<String, Object> raw) {
    }

    public QuestionTemplateConfiguration parse(QuestionTemplate template) {
        return parseConfiguration(template == null ? null : template.getConfiguration());
    }

    /**
     * Parses a raw {@code configuration} JSON blob.
     *
     * <p>Exists so a caller that has already read the configuration column can parse it without
     * reconstructing an entity (GAP-156's batch path). Routes through the same logic as
     * {@link #parse(QuestionTemplate)} so both read the flag identically.
     */
    public QuestionTemplateConfiguration parseConfiguration(String configuration) {
        if (configuration == null || configuration.isBlank()) {
            return new QuestionTemplateConfiguration(false, Map.of());
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(configuration, Map.class);
            Object flag = parsed.get("requires_latex_parsing");
            boolean requiresLatex = flag instanceof Boolean booleanFlag && booleanFlag;
            return new QuestionTemplateConfiguration(requiresLatex, parsed);
        } catch (Exception invalid) {
            // A malformed configuration must not break authoring; treat it as the safe default.
            return new QuestionTemplateConfiguration(false, Map.of());
        }
    }
}
