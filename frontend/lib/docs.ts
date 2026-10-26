import fs from "node:fs";
import path from "node:path";

/**
 * Build-time loader for the bundled public docs ({@code ADR-0036}).
 *
 * <p>Content lives in {@code frontend/content/docs/}, one markdown file per page. This module is
 * only imported from server components so it is never shipped to the browser. The internal
 * {@code docs/} and {@code specs/} folders are intentionally not published.
 */

export interface DocsPage {
  slug: string;
  href: string;
  title: string;
  content: string;
  order: number;
}

const DOCS_DIR = path.join(process.cwd(), "content", "docs");

function parseDoc(slug: string, raw: string): DocsPage {
  let title = slug.replace(/-/g, " ");
  let order = 99;
  let content = raw;

  // Frontmatter: a leading "---" block with "key: value" lines, then the markdown body.
  if (raw.startsWith("---")) {
    const closing = raw.indexOf("---", 3);
    if (closing !== -1) {
      const frontmatter = raw.slice(3, closing);
      let body = raw.slice(closing + 3);
      if (body.startsWith("\r\n")) {
        body = body.slice(2);
      } else if (body.startsWith("\n")) {
        body = body.slice(1);
      }
      content = body;

      for (const line of frontmatter.split("\n")) {
        const trimmed = line.trim();
        if (trimmed.startsWith("title:")) {
          title = trimmed.slice("title:".length).trim();
        } else if (trimmed.startsWith("order:")) {
          const parsed = Number.parseInt(trimmed.slice("order:".length).trim(), 10);
          if (!Number.isNaN(parsed)) {
            order = parsed;
          }
        }
      }
    }
  }

  return {
    slug,
    href: slug === "overview" ? "/docs" : `/docs/${slug}`,
    title,
    content,
    order,
  };
}

export function getDocsPages(): DocsPage[] {
  const files = fs.readdirSync(DOCS_DIR).filter((file) => file.endsWith(".md"));
  return files
    .map((file) => parseDoc(file.slice(0, -3), fs.readFileSync(path.join(DOCS_DIR, file), "utf8")))
    .sort((a, b) => a.order - b.order);
}

export function getDocPage(slug: string): DocsPage | undefined {
  const file = path.join(DOCS_DIR, `${slug}.md`);
  if (!fs.existsSync(file)) return undefined;
  return parseDoc(slug, fs.readFileSync(file, "utf8"));
}

export function getAllDocSlugs(): string[] {
  return getDocsPages().map((page) => page.slug).filter((slug) => slug !== "overview");
}

/** Version anchor shown at /docs ("Documentation for v1.5"). */
export const DOCS_VERSION = "v1.5";
