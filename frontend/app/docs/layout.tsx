import type { ReactNode } from "react";

import { DocsLayout } from "@/components/docs/docs-layout";
import { DOCS_VERSION, getDocsPages } from "@/lib/docs";

/** Public documentation shell ({@code ADR-0036}). Built once from the bundled markdown. */
export default function DocsRootLayout({ children }: { children: ReactNode }) {
  const pages = getDocsPages();
  const nav = pages.map(({ href, title }) => ({ href, title }));
  const searchIndex = pages.map(({ href, title, content }) => ({ href, title, text: content }));

  return (
    <DocsLayout nav={nav} searchIndex={searchIndex} version={DOCS_VERSION}>
      {children}
    </DocsLayout>
  );
}
