"use client";

import { renderTemplatePreview } from "@/features/communication/schemas/communication";
import type { EmailTemplate } from "@/features/communication/schemas/communication";

/**
 * A safe template preview ({@code specs/19-P10-Communication-V1.5.md §70-§72}).
 *
 * <p>Placeholders are replaced with the frozen synthetic context. The body is shown as text, never
 * injected as HTML: the server sanitizes admin HTML before sending, and executing it in the admin
 * browser would create the XSS path §72 prohibits.
 */
export function TemplatePreview({ template }: { template: EmailTemplate }) {
  const subject = renderTemplatePreview(template.subject ?? "");
  const body = renderTemplatePreview(template.body ?? "");

  return (
    <section aria-label="Template preview" className="space-y-4">
      <p className="text-sm text-slate-500">
        Synthetic preview with sample context. Nothing is sent, the production provider is not
        called, and the body is displayed as text.
      </p>

      <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
        <h2 className="text-sm font-semibold text-slate-700">Subject</h2>
        <p className="mt-1 text-sm">{subject.length > 0 ? subject : "(no subject)"}</p>
      </div>

      <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
        <h2 className="text-sm font-semibold text-slate-700">Body</h2>
        <pre className="mt-2 whitespace-pre-wrap break-words rounded bg-slate-50 p-4 text-sm text-slate-800">
          {body.length > 0 ? body : "(no body)"}
        </pre>
      </div>

      <p className="text-xs text-slate-500">
        The server sanitizes admin HTML against the platform allow-list before sending. This preview
        never executes it.
      </p>
    </section>
  );
}
