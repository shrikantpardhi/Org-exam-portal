package com.company.exam.communication.service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * The fixed template renderer ({@code specs/19-P10-Communication-V1.5.md §24, §26, §27}).
 *
 * <p>Variables use {@code {{variableName}}} syntax and rendering is a <strong>pure string
 * replacement</strong> against a controlled model. There is no expression evaluation, method
 * invocation, scripting, filesystem or network access. Unresolved placeholders are left as-is so the
 * gap is visible rather than silently blank.
 */
@Component
public class TemplateRenderer {

    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_]+)\\s*}}");
    private static final int MAX_LENGTH = 200_000;

    /** Renders the text by replacing known placeholders; unknown placeholders are preserved. */
    public String render(String template, Map<String, String> model) {
        if (template == null) {
            return null;
        }
        Matcher matcher = VARIABLE.matcher(template);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = model == null ? null : model.get(name);
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(value == null ? matcher.group(0) : value));
            if (rendered.length() > MAX_LENGTH) {
                throw new IllegalStateException("Rendered template exceeds the permitted length");
            }
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }
}
