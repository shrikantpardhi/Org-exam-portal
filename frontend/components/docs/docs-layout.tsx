"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useMemo, useState, type ReactNode } from "react";


export interface DocsNavItem {
  href: string;
  title: string;
}

export interface DocsSearchItem {
  href: string;
  title: string;
  text: string;
}

function searchDocs(query: string, index: DocsSearchItem[]): DocsNavItem[] {
  const q = query.toLowerCase();
  return index
    .filter((item) => item.title.toLowerCase().includes(q) || item.text.toLowerCase().includes(q))
    .slice(0, 10)
    .map(({ href, title }) => ({ href, title }));
}

/**
 * Public documentation shell ({@code ADR-0036}): top header (logo, search, theme, sign in), left
 * page sidebar, content region, and version indicator. No token is required.
 */
export function DocsLayout({
 nav,
 searchIndex,
 version,
 children,
}: {
 nav: DocsNavItem[];
 searchIndex: DocsSearchItem[];
 version: string;
 children: ReactNode;
}) {
 const pathname = usePathname();
 const [query, setQuery] = useState("");
  const [searchOpen, setSearchOpen] = useState(false);
  const results = useMemo(() => (query.trim() ? searchDocs(query.trim(), searchIndex) : []), [query, searchIndex]);

  return (
    <div className="flex min-h-screen flex-col">
      <header className="sticky top-0 z-40 border-b border-border bg-surface/95 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-7xl items-center justify-between gap-4 px-6">
          <div className="flex items-center gap-6">
            <Link href="/" className="text-lg font-semibold tracking-tight text-foreground">
              Exam Platform
            </Link>
            <span className="hidden text-sm text-muted-foreground sm:inline">Docs</span>
          </div>
          <div className="relative">
            <input
 type="search"
              value={query}
              onChange={(e) => { setQuery(e.target.value); setSearchOpen(true); }}
              onFocus={() => setSearchOpen(true)}
              onBlur={() => setTimeout(() => setSearchOpen(false), 150)}
 placeholder="Search docs…"
 aria-label="Search documentation"
 className="w-40 rounded-md border border-input px-3 py-1.5 text-sm sm:w-64"
            />
            {searchOpen && results.length > 0 ? (
              <div className="absolute right-0 top-full z-50 mt-2 w-72 rounded-md border border-border bg-surface p-1 shadow-lg">
                {results.map((result) => (
                  <Link
                    key={result.href}
                    href={result.href}
                    onMouseDown={(e) => e.preventDefault()}
 className="block rounded px-3 py-2 text-sm text-foreground hover:bg-muted"
                  >
                    {result.title}
                  </Link>
                ))}
              </div>
            ) : null}
          </div>
          <div className="flex items-center gap-3">
            <Link
 href="/login"
 className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-hover"
            >
              Sign in
            </Link>
          </div>
        </div>
      </header>

      <div className="mx-auto flex w-full max-w-7xl flex-1 items-start gap-8 px-6 py-8">
        <aside className="sticky top-24 hidden w-56 shrink-0 md:block">
          <p className="mb-3 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            Documentation for {version}
          </p>
          <nav className="space-y-1 border-l border-border">
            {nav.map((item) => {
              const active = pathname === item.href;
              return (
                <Link
 key={item.href}
 href={item.href}
 aria-current={active ? "page" : undefined}
 className={
 active
 ? "block border-l-2 border-primary py-1.5 pl-4 text-sm font-medium text-foreground"
 : "block border-l-2 border-transparent py-1.5 pl-4 text-sm text-muted-foreground hover:text-foreground"
                  }
                >
                  {item.title}
                </Link>
              );
            })}
          </nav>
        </aside>

        <main className="min-w-0 flex-1 pb-16">{children}</main>
      </div>
    </div>
  );
}
