import { notFound } from "next/navigation";

import { ApiReference } from "@/components/docs/api-reference";
import { DocsArticle } from "@/components/docs/docs-article";
import { getAllDocSlugs, getDocPage } from "@/lib/docs";
import { extractHeadings } from "@/lib/markdown";
import { loadApiReference } from "@/lib/openapi";

export const dynamicParams = false;

export function generateStaticParams() {
  return getAllDocSlugs().map((slug) => ({ slug }));
}

/** A bundled public docs page ({@code ADR-0036}). */
export default async function DocsSlugPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const page = getDocPage(slug);
  if (!page) notFound();

  // The API reference is generated from the retained OpenAPI document at build time (GAP-170).
  if (slug === "api") {
    const reference = loadApiReference();
    return (
      <div>
        <DocsArticle markdown={page.content} headings={extractHeadings(page.content)} />
        <div className="mt-10">
          <ApiReference endpoints={reference.endpoints} tags={reference.tags} />
        </div>
      </div>
    );
  }

  return <DocsArticle markdown={page.content} headings={extractHeadings(page.content)} />;
}
