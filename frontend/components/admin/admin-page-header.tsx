"use client";

import type { ReactNode } from "react";

/** The consistent admin screen heading ({@code specs/20-P11-Administration-V1.5.md §123}). */
export function AdminPageHeader({
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
        <p className="text-sm text-muted-foreground">Administration</p>
        <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
        {description ? <p className="text-sm text-muted-foreground">{description}</p> : null}
      </div>
      {actions ? <div className="flex flex-wrap gap-2">{actions}</div> : null}
    </header>
  );
}
