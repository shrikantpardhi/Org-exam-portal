"use client";

/**
 * Canonical content preview ({@code specs/12-P3-Content-V1.5.md §56, §57, §168}).
 *
 * <p>The HTML is produced by the server-side canonical renderer: it is MARKDOWN_LATEX_V1, raw HTML
 * is rejected, output is sanitized, and math is carried in controlled {@code .katex-tex}
 * placeholders. Editor preview and delivery use the same renderer semantics — there is no
 * permissive editor renderer.
 */
export function VersionPreview({ html }: { html: string | null | undefined }) {
  if (!html) {
    return <p className="text-sm text-slate-500">No rendered content.</p>;
  }
  return (
    <div
      role="document"
      aria-label="Rendered question content"
      className="prose max-w-none text-sm"
      dangerouslySetInnerHTML={{ __html: html }}
    />
  );
}
