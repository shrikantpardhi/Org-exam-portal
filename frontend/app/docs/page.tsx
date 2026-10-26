import { notFound } from "next/navigation";

import { DocsArticle } from "@/components/docs/docs-article";
import { getDocPage } from "@/lib/docs";
import { extractHeadings } from "@/lib/markdown";

/** The /docs landing page (overview). */
export default function DocsOverviewPage() {
  const page = getDocPage("overview");
  if (!page) notFound();
  return <DocsArticle markdown={page.content} headings={extractHeadings(page.content)} />;
}
