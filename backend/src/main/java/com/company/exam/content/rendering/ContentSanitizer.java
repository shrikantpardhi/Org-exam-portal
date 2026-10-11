package com.company.exam.content.rendering;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Final defensive pass over generated HTML ({@code specs/12-P3-Content-V1.5.md §16, §41, §57}).
 *
 * <p>The renderer escapes all author text and emits only its own tags, so output is safe by
 * construction. This component re-checks and removes active constructs as defence in depth so
 * preview and delivery never execute author-controlled code.
 */
@Component
public class ContentSanitizer {

    private static final Pattern SCRIPT = Pattern.compile("(?is)<script.*?</script>");
    private static final Pattern EVENT_HANDLER = Pattern.compile("(?i)\\son[a-z]+\\s*=\\s*\"[^\"]*\"");
    private static final Pattern JAVASCRIPT_URL = Pattern.compile("(?i)javascript:");

    public String sanitize(String html) {
        if (html == null) {
            return "";
        }
        String sanitized = SCRIPT.matcher(html).replaceAll("");
        sanitized = EVENT_HANDLER.matcher(sanitized).replaceAll("");
        sanitized = JAVASCRIPT_URL.matcher(sanitized).replaceAll("");
        return sanitized;
    }
}
