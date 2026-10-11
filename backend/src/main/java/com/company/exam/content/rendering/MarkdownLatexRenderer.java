package com.company.exam.content.rendering;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Renders canonical {@code MARKDOWN_LATEX_V1} content to safe HTML
 * ({@code specs/12-P3-Content-V1.5.md §16, §41, §88}).
 *
 * <p>Pipeline: controlled Markdown AST -&gt; LaTeX delimiter handling -&gt; math rendering
 * boundary -&gt; asset shortcode resolution -&gt; sanitization. All author text is HTML-escaped and
 * only the renderer's own tags are emitted, so raw HTML and executable MDX cannot reach the output.
 *
 * <p><b>Math boundary.</b> This module enforces the security and delimiter contract and emits a
 * controlled math placeholder carrying the TeX source ({@code <span class="katex-tex"
 * data-tex="...">}). KaTeX server-side rendering is performed at the Next.js rendering boundary
 * (the server-side rendering tier of this stack); see
 * {@code docs/decisions/ADR-0016-p3-content-rendering-boundary.md}. The renderer version is part of
 * the cache identity so historical output is reproducible.
 */
@Component
public class MarkdownLatexRenderer {

    public static final String CONTENT_FORMAT = "MARKDOWN_LATEX_V1";
    public static final String RENDERER_VERSION = "1.0.0";

    private static final Pattern BLOCK_MATH = Pattern.compile("\\\\\\[(.*?)\\\\\\]", Pattern.DOTALL);
    private static final Pattern INLINE_MATH = Pattern.compile("\\\\\\((.*?)\\\\\\)", Pattern.DOTALL);
    private static final Pattern ASSET_SHORTCODE = Pattern.compile("\\[asset:\\s*([^\\]]*)\\]");
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");
    private static final Pattern UNORDERED = Pattern.compile("^\\s*[-*]\\s+(.*)$");
    private static final Pattern ORDERED = Pattern.compile("^\\s*\\d+\\.\\s+(.*)$");
    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern ITALIC = Pattern.compile("\\*(.+?)\\*");
    private static final Pattern CODE = Pattern.compile("`([^`]+)`");

    private final ContentSanitizer sanitizer;

    public MarkdownLatexRenderer(ContentSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    public RenderedContent render(String content) {
        String source = content == null ? "" : content;
        Map<String, String> tokens = new LinkedHashMap<>();
        int[] counter = {0};
        List<UUID> assetIds = new ArrayList<>();

        String working = replaceTokens(source, BLOCK_MATH, m -> mathHtml(m.group(1), true), tokens, counter);
        working = replaceTokens(working, INLINE_MATH, m -> mathHtml(m.group(1), false), tokens, counter);
        working = replaceTokens(working, ASSET_SHORTCODE, m -> {
            String raw = m.group(1).trim();
            try {
                UUID id = UUID.fromString(raw);
                assetIds.add(id);
                return "<figure class=\"asset\" data-asset-id=\"" + id + "\"></figure>";
            } catch (IllegalArgumentException malformed) {
                return "";
            }
        }, tokens, counter);

        String html = renderMarkdown(working);
        for (Map.Entry<String, String> entry : tokens.entrySet()) {
            html = html.replace(entry.getKey(), entry.getValue());
        }
        return new RenderedContent(sanitizer.sanitize(html), CONTENT_FORMAT, RENDERER_VERSION, assetIds);
    }

    private String replaceTokens(String input, Pattern pattern, Function<Matcher, String> factory,
            Map<String, String> tokens, int[] counter) {
        Matcher matcher = pattern.matcher(input);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String token = "\u0000T" + counter[0]++ + "\u0000";
            tokens.put(token, factory.apply(matcher));
            matcher.appendReplacement(builder, Matcher.quoteReplacement(token));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    private String mathHtml(String tex, boolean display) {
        String escaped = escapeHtml(tex.trim());
        String cssClass = display ? "katex-tex katex-block" : "katex-tex";
        return "<span class=\"" + cssClass + "\" data-display=\"" + display + "\" data-tex=\""
                + escaped + "\"></span>";
    }

    private String renderMarkdown(String input) {
        String escaped = escapeHtml(input);
        String[] lines = escaped.split("\n", -1);
        StringBuilder out = new StringBuilder();
        List<String> paragraph = new ArrayList<>();
        List<String> list = new ArrayList<>();
        boolean orderedList = false;
        boolean inCode = false;
        StringBuilder code = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.strip();

            if (trimmed.startsWith("```")) {
                out.append(flushParagraph(paragraph));
                out.append(flushList(list, orderedList));
                if (inCode) {
                    out.append("<pre><code>").append(code).append("</code></pre>");
                    code.setLength(0);
                    inCode = false;
                } else {
                    inCode = true;
                }
                continue;
            }
            if (inCode) {
                code.append(line).append("\n");
                continue;
            }
            if (trimmed.isEmpty()) {
                out.append(flushParagraph(paragraph));
                out.append(flushList(list, orderedList));
                continue;
            }

            Matcher heading = HEADING.matcher(trimmed);
            if (heading.matches()) {
                out.append(flushParagraph(paragraph));
                out.append(flushList(list, orderedList));
                int level = heading.group(1).length();
                out.append("<h").append(level).append(">").append(inline(heading.group(2)))
                        .append("</h").append(level).append(">");
                continue;
            }

            Matcher unordered = UNORDERED.matcher(line);
            if (unordered.matches()) {
                out.append(flushParagraph(paragraph));
                if (orderedList && !list.isEmpty()) {
                    out.append(flushList(list, orderedList));
                }
                orderedList = false;
                list.add(unordered.group(1));
                continue;
            }

            Matcher ordered = ORDERED.matcher(line);
            if (ordered.matches()) {
                out.append(flushParagraph(paragraph));
                if (!orderedList && !list.isEmpty()) {
                    out.append(flushList(list, orderedList));
                }
                orderedList = true;
                list.add(ordered.group(1));
                continue;
            }

            out.append(flushList(list, orderedList));
            paragraph.add(trimmed);
        }

        out.append(flushParagraph(paragraph));
        out.append(flushList(list, orderedList));
        if (inCode) {
            out.append("<pre><code>").append(code).append("</code></pre>");
        }
        return out.toString();
    }

    private String flushParagraph(List<String> paragraph) {
        if (paragraph.isEmpty()) {
            return "";
        }
        String html = "<p>" + inline(String.join(" ", paragraph)) + "</p>";
        paragraph.clear();
        return html;
    }

    private String flushList(List<String> list, boolean ordered) {
        if (list.isEmpty()) {
            return "";
        }
        String tag = ordered ? "ol" : "ul";
        StringBuilder out = new StringBuilder("<").append(tag).append(">");
        for (String item : list) {
            out.append("<li>").append(inline(item)).append("</li>");
        }
        out.append("</").append(tag).append(">");
        list.clear();
        return out.toString();
    }

    private String inline(String text) {
        String result = CODE.matcher(text).replaceAll("<code>$1</code>");
        result = BOLD.matcher(result).replaceAll("<strong>$1</strong>");
        result = ITALIC.matcher(result).replaceAll("<em>$1</em>");
        return result;
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
