import type { ReactNode } from "react";

/** The consistent platform screen heading (docs/frontend/navigation.md). */
export function PlatformPageHeader({
  title,
  description,
  actions,
}: {
  title: string;
  description?: string;
  actions?: ReactNode;
}) {
  return (
    <header className="flex flex-wrap items-start justify-between gap-3 border-b border-border pb-4">
      <div className="space-y-1">
        {/*
          No eyebrow line: the breadcrumb root above already says "Platform", so a second, static
          "Platform" paragraph was the same word twice within an inch of page chrome.
        */}
        <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
        {description ? <p className="text-sm text-muted-foreground">{description}</p> : null}
      </div>
      {actions ? <div className="flex flex-wrap gap-2">{actions}</div> : null}
    </header>
  );
}
