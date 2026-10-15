package com.company.exam.reporting.renderer;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.exam.reporting.projection.ReportProjection;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Renderer verification ({@code specs/22-P13-Reporting-V1.5.md §19, §20}).
 *
 * <p>In particular this proves the CSV spreadsheet formula-injection mitigation and that the PDF
 * writer emits a structurally complete document.
 */
class P13RendererTest {

    private final CsvRenderer csv = new CsvRenderer();
    private final PdfRenderer pdf = new PdfRenderer();

    private ReportProjection projection(List<Map<String, String>> rows) {
        return new ReportProjection("Test Report", "All time", Map.of("k", "v"), Instant.parse("2026-10-15T00:00:00Z"),
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                List.of(new ReportProjection.Column("a", "A"), new ReportProjection.Column("b", "B")), rows,
                List.of(new ReportProjection.SummaryMetric("rows", Integer.toString(rows.size()),
                        "Count of returned rows")),
                null);
    }

    // ------------------------------------------------------------------ CSV

    @Test
    void csvNeutralizesFormulaLikePrefixes() {
        String rendered = new String(csv.render(projection(List.of(
                Map.of("a", "=1+1", "b", "@SUM(A1)"),
                Map.of("a", "+44", "b", "-2")))),
                StandardCharsets.UTF_8);
        // Every dangerous cell is prefixed so a spreadsheet treats it as text, never a formula.
        assertThat(rendered).contains("'=1+1").contains("'@SUM(A1)").contains("'+44").contains("'-2");
    }

    @Test
    void csvQuotesEmbeddedDelimitersQuotesAndLineBreaks() {
        assertThat(csv.escape("a,b")).isEqualTo("\"a,b\"");
        assertThat(csv.escape("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        // An embedded line break is normalized to a space, so the field cannot forge a new record and
        // needs no quoting (§20 "normalizes line breaks").
        assertThat(csv.escape("line1\nline2")).isEqualTo("line1 line2");
        assertThat(csv.escape("line1\r\nline2")).isEqualTo("line1 line2");
    }

    @Test
    void csvHasDeterministicHeaderAndRows() {
        String rendered = new String(csv.render(projection(List.of(Map.of("a", "1", "b", "2")))),
                StandardCharsets.UTF_8);
        assertThat(rendered).startsWith("\uFEFF");
        assertThat(rendered).contains("A,B\r\n");
        assertThat(rendered).contains("1,2\r\n");
    }

    @Test
    void csvRendersAnEmptyResultSetCleanly() {
        String rendered = new String(csv.render(ReportProjection.empty("Empty", "All time", Map.of(),
                Instant.now(), null)), StandardCharsets.UTF_8);
        assertThat(rendered).isNotNull().startsWith("\uFEFF");
    }

    // ------------------------------------------------------------------ PDF

    @Test
    void pdfIsAStructurallyCompleteDocument() {
        byte[] document = pdf.render(projection(List.of(Map.of("a", "1", "b", "2"))));
        String text = new String(document, StandardCharsets.ISO_8859_1);
        assertThat(text).startsWith("%PDF-1.4");
        assertThat(text).endsWith("%%EOF\n");
        assertThat(text).contains("/Type /Catalog").contains("/Type /Pages").contains("/Type /Page")
                .contains("/BaseFont /Helvetica");
        assertThat(text).contains("xref").contains("trailer").contains("/Root 1 0 R");
        assertThat(text).contains("Test Report");
        assertThat(text).contains("Page 1 of 1");
    }

    @Test
    void pdfPaginatesAndNumbersEveryPage() {
        StringBuilder large = new StringBuilder();
        List<Map<String, String>> rows = new java.util.ArrayList<>();
        for (int index = 0; index < 400; index++) {
            rows.add(Map.of("a", "row-" + index, "b", large.append(index).toString()));
        }
        String text = new String(pdf.render(projection(rows)), StandardCharsets.ISO_8859_1);
        assertThat(text).contains("Page 1 of ");
        assertThat(text).contains("Page 2 of ");
    }

    @Test
    void pdfEscapesDelimitersAndDropsUnrepresentableCharacters() {
        byte[] document = pdf.render(projection(List.of(Map.of("a", "a(b)c\\d", "b", "\u03C0"))));
        String text = new String(document, StandardCharsets.ISO_8859_1);
        assertThat(text).contains("a\\(b\\)c\\\\d");
        assertThat(text).contains("?"); // non-representable glyph is replaced, never emitted raw
    }

    @Test
    void pdfRendersAnEmptyResultSetWithAMessage() {
        String text = new String(pdf.render(ReportProjection.empty("Empty Report", "All time", Map.of(),
                Instant.now(), null)), StandardCharsets.ISO_8859_1);
        assertThat(text).contains("No matching data");
    }
}
