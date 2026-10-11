package com.company.exam.content.rendering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.exam.content.config.ContentProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The content rendering regression corpus
 * ({@code specs/12-P3-Content-V1.5.md §136}, {@code specs/08-Exam-Engine-and-CDUI-Contract-V1.5.md §88}).
 *
 * <p>Proves MARKDOWN_LATEX_V1: currency stays text, only {@code \(...\)} and {@code \[...\]} are
 * math, raw HTML and executable MDX are rejected, and asset shortcodes are validated.
 */
class ContentRenderingRegressionTest {

    private final ContentProperties properties = new ContentProperties();
    private final ContentValidator validator = new ContentValidator(properties);
    private final MarkdownLatexRenderer renderer = new MarkdownLatexRenderer(new ContentSanitizer());

    @Test
    void ordinaryTextIsReturnedAsMarkup() {
        RenderedContent rendered = renderer.render("An ordinary question.");

        assertThat(rendered.html()).contains("An ordinary question.");
        assertThat(rendered.html()).doesNotContain("katex-tex");
        assertThat(rendered.contentFormat()).isEqualTo(MarkdownLatexRenderer.CONTENT_FORMAT);
    }

    @Test
    void currencyDollarsRemainOrdinaryText() {
        RenderedContent rendered = renderer.render("An item costs $500 and the tax is $50.");

        assertThat(rendered.html()).contains("$500").contains("$50");
        assertThat(rendered.html()).doesNotContain("katex-tex");
    }

    @Test
    void inlineLatexIsRenderedAsInlineMath() {
        RenderedContent rendered = renderer.render("Solve for \\(x\\) when \\(2x+5=15\\).");

        assertThat(rendered.html()).contains("katex-tex").contains("data-display=\"false\"");
        assertThat(rendered.html()).doesNotContain("\\(");
    }

    @Test
    void blockLatexIsRenderedAsBlockMath() {
        RenderedContent rendered = renderer.render("\\[ x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a} \\]");

        assertThat(rendered.html()).contains("katex-block");
    }

    @Test
    void dollarDelimiterIsNotTreatedAsMath() {
        RenderedContent rendered = renderer.render("$x$ is not math");

        assertThat(rendered.html()).contains("$x$");
        assertThat(rendered.html()).doesNotContain("katex-tex");
    }

    @Test
    void mixedCurrencyAndMathCoexist() {
        RenderedContent rendered = renderer.render("The cost is $500 and the area is \\(A = \\pi r^2\\).");

        assertThat(rendered.html()).contains("$500").contains("data-tex=");
    }

    @Test
    void doubleDollarIsRejectedAsAnUnsupportedDelimiter() {
        assertThatThrownBy(() -> validator.validate("$$x$$", "content"))
                .isInstanceOf(ContentValidationException.class)
                .hasMessageContaining("Content validation failed");
    }

    @Test
    void rawHtmlIsRejected() {
        for (String html : List.of("<p>Question</p>", "<strong>x</strong>", "<div>y</div>",
                "<script>alert(1)</script>", "<img src=\"x\">", "<span onclick=\"x\">y</span>")) {
            assertThatThrownBy(() -> validator.validate(html, "content"))
                    .as("raw HTML must be rejected: %s", html)
                    .isInstanceOf(ContentValidationException.class);
        }
    }

    @Test
    void executableMdxIsRejected() {
        assertThatThrownBy(() -> validator.validate("import x from 'somewhere'", "content"))
                .isInstanceOf(ContentValidationException.class);
    }

    @Test
    void unbalancedMathDelimitersAreRejected() {
        assertThatThrownBy(() -> validator.validate("Solve \\(x", "content"))
                .isInstanceOf(ContentValidationException.class);
    }

    @Test
    void malformedAssetShortcodeIsRejected() {
        assertThatThrownBy(() -> validator.validate("[asset: not-a-uuid]", "content"))
                .isInstanceOf(ContentValidationException.class);
    }

    @Test
    void validAssetShortcodeIsAcceptedAndResolved() {
        String content = "See below.\n\n[asset: 550e8400-e29b-41d4-a716-446655440000]";

        List<java.util.UUID> assetIds = validator.validate(content, "content");
        RenderedContent rendered = renderer.render(content);

        assertThat(assetIds).containsExactly(java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        assertThat(rendered.html()).contains("data-asset-id=\"550e8400-e29b-41d4-a716-446655440000\"");
    }

    @Test
    void contentOverTheConfiguredLimitIsRejected() {
        ContentProperties small = new ContentProperties();
        small.setMaxContentLength(5);
        ContentValidator smallValidator = new ContentValidator(small);

        assertThatThrownBy(() -> smallValidator.validate("123456", "content"))
                .isInstanceOf(ContentValidationException.class);
    }

    @Test
    void rendererOutputIsSanitizedDefensively() {
        // Even if a placeholder somehow carried active markup, the sanitizer removes it.
        ContentSanitizer sanitizer = new ContentSanitizer();
        assertThat(sanitizer.sanitize("<p>ok</p><script>alert(1)</script>"))
                .isEqualTo("<p>ok</p>");
        assertThat(sanitizer.sanitize("<a onclick=\"x\">y</a>")).doesNotContain("onclick");
    }
}
