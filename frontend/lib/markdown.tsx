import { Fragment, type ReactNode } from "react";

/**
 * A deliberately small, safe markdown renderer for the public docs ({@code ADR-0036}).
 *
 * <p>It supports the subset the curated docs use — headings, paragraphs, bold/italic, inline
 * code, fenced code blocks, unordered and ordered lists, links, blockquotes, and horizontal
 * rules. It emits no raw HTML and no executable MDX (frozen rule 17), and unknown constructs are
 * rendered as literal text rather than executed.
 */

export function slugify(text: string): string {
  return text
    .toLowerCase()
    .replace(/[^a-z0-9\s-]/g, "")
    .trim()
    .replace(/\s+/g, "-");
}

export interface DocHeading {
  id: string;
  text: string;
  level: number;
}

export function extractHeadings(markdown: string): DocHeading[] {
  const headings: DocHeading[] = [];
  let inCode = false;
  for (const line of markdown.split("\n")) {
    if (line.startsWith("```")) {
      inCode = !inCode;
      continue;
    }
    if (inCode) continue;
    const match = line.match(/^(#{1,6})\s+(.*)$/);
    if (match) {
      const [, hashes = "", headingText = ""] = match;
      const text = headingText.trim();
      headings.push({ id: slugify(text), text, level: hashes.length });
    }
  }
  return headings;
}

function parseInline(text: string): ReactNode[] {
  const nodes: ReactNode[] = [];
  let i = 0;
  let key = 0;
  while (i < text.length) {
    const rest = text.slice(i);
    const code = rest.match(/^`([^`]+)`/);
    if (code) {
      nodes.push(<code key={key++} className="rounded bg-slate-100 px-1 py-0.5 text-sm dark:bg-slate-800">{code[1]}</code>);
      i += code[0].length;
      continue;
    }
    const link = rest.match(/^\[([^\]]+)\]\(([^)]+)\)/);
    if (link) {
      const [, label = "", url = ""] = link;
      nodes.push(
        <a key={key++} href={url} className="text-slate-900 underline dark:text-white">
          {parseInline(label)}
        </a>,
      );
      i += link[0].length;
      continue;
    }
    const bold = rest.match(/^\*\*([^*]+)\*\*/);
    if (bold) {
      nodes.push(<strong key={key++}>{bold[1]}</strong>);
      i += bold[0].length;
      continue;
    }
    const italic = rest.match(/^\*([^*]+)\*/);
    if (italic) {
      nodes.push(<em key={key++}>{italic[1]}</em>);
      i += italic[0].length;
      continue;
    }
    const next = rest.search(/[`*[]/);
    const end = next === -1 ? text.length : i + next;
    nodes.push(text.slice(i, end));
    i = end;
  }
  return nodes;
}

function heading(level: number, text: string) {
  const id = slugify(text);
  const content = parseInline(text);
  const className = "scroll-mt-24 font-semibold text-slate-900 dark:text-white";
  switch (level) {
    case 1: return <h1 id={id} className={`text-3xl ${className}`}>{content}</h1>;
    case 2: return <h2 id={id} className={`mt-10 text-2xl ${className}`}>{content}</h2>;
    case 3: return <h3 id={id} className={`mt-8 text-xl ${className}`}>{content}</h3>;
    case 4: return <h4 id={id} className={`mt-6 text-lg ${className}`}>{content}</h4>;
    case 5: return <h5 id={id} className={`mt-4 text-base ${className}`}>{content}</h5>;
    default: return <h6 id={id} className={`mt-4 text-sm ${className}`}>{content}</h6>;
  }
}

export function renderMarkdown(markdown: string): ReactNode {
  const blocks: ReactNode[] = [];
  let list: { ordered: boolean; items: ReactNode[] } | null = null;
  let codeLines: string[] | null = null;
  let codeLang = "";
  let key = 0;

  const flushList = () => {
    if (list) {
      const items = list.items;
      const node = list.ordered
        ? <ol key={key++} className="my-4 list-decimal space-y-1 pl-6">{items}</ol>
        : <ul key={key++} className="my-4 list-disc space-y-1 pl-6">{items}</ul>;
      blocks.push(node);
      list = null;
    }
  };
  const flushCode = () => {
    if (codeLines) {
      blocks.push(
        <pre key={key++} className="my-4 overflow-x-auto rounded-lg bg-slate-100 p-4 text-sm dark:bg-slate-800">
          <code>{codeLines.join("\n")}</code>
        </pre>,
      );
      codeLines = null;
    }
  };

  for (const line of markdown.split("\n")) {
    if (line.startsWith("```")) {
      if (codeLines) {
        flushCode();
      } else {
        flushList();
        codeLines = [];
        codeLang = line.slice(3).trim();
      }
      continue;
    }
    if (codeLines) {
      codeLines.push(line);
      continue;
    }

    const h = line.match(/^(#{1,6})\s+(.*)$/);
    if (h) {
      const [, hashes = "", headingText = ""] = h;
      flushList();
      blocks.push(<Fragment key={key++}>{heading(hashes.length, headingText.trim())}</Fragment>);
      continue;
    }
    if (/^\s*(-{3,}|\*{3,})\s*$/.test(line)) {
      flushList();
      blocks.push(<hr key={key++} className="my-6 border-slate-200 dark:border-slate-800" />);
      continue;
    }
    const ul = line.match(/^\s*[-*+]\s+(.*)$/);
    const ol = line.match(/^\s*\d+[.)]\s+(.*)$/);
    if (ul || ol) {
      const ordered = Boolean(ol);
      const text = ul?.[1] ?? ol?.[1] ?? "";
      if (!list || list.ordered !== ordered) {
        flushList();
        list = { ordered, items: [] };
      }
      list.items.push(<li key={key++}>{parseInline(text)}</li>);
      continue;
    }
    if (line.startsWith("> ")) {
      flushList();
      blocks.push(
        <blockquote key={key++} className="my-4 border-l-4 border-slate-300 pl-4 text-slate-600 dark:border-slate-700 dark:text-slate-400">
          {parseInline(line.slice(2))}
        </blockquote>,
      );
      continue;
    }
    if (line.trim() === "") {
      flushList();
      continue;
    }
    flushList();
    blocks.push(<p key={key++} className="my-4 leading-relaxed text-slate-700 dark:text-slate-300">{parseInline(line)}</p>);
  }

  flushList();
  flushCode();
  void codeLang;
  return <>{blocks}</>;
}
