import type { ReactNode } from "react";
import type * as React from "react";

import { cn } from "@/lib/utils";

/**
 * Responsive table shell.
 *
 * <p>Two behaviours, both load-bearing. The wrapper scrolls horizontally so a wide table never forces
 * the whole page to overflow (WCAG 1.4.10) — `tabIndex=0` is on the wrapper so a keyboard user can
 * scroll it, with a role and label so the control is announced. `min-w` keeps columns readable rather
 * than crushing them into an unreadable grid on a phone.
 */
export function TableWrapper({
  children,
  label,
  className,
}: {
  children: ReactNode;
  label: string;
  className?: string;
}) {
  return (
    <div
      role="region"
      aria-label={label}
      tabIndex={0}
      className={cn("w-full overflow-x-auto rounded-lg border border-border bg-surface", className)}
    >
      {children}
    </div>
  );
}

export function Table({ className, ...props }: React.TableHTMLAttributes<HTMLTableElement>) {
  return <table className={cn("w-full min-w-[640px] caption-bottom text-sm", className)} {...props} />;
}

export function THead({ className, ...props }: React.HTMLAttributes<HTMLTableSectionElement>) {
  return <thead className={cn("border-b border-border bg-muted/60", className)} {...props} />;
}

export function TBody({ className, ...props }: React.HTMLAttributes<HTMLTableSectionElement>) {
  return <tbody className={cn("divide-y divide-border", className)} {...props} />;
}

export function TR({ className, ...props }: React.HTMLAttributes<HTMLTableRowElement>) {
  return <tr className={cn("transition-colors hover:bg-muted/40", className)} {...props} />;
}

export function TH({ className, scope = "col", ...props }: React.ThHTMLAttributes<HTMLTableCellElement>) {
  return (
    <th
      scope={scope}
      className={cn("px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-muted-foreground", className)}
      {...props}
    />
  );
}

export function TD({ className, ...props }: React.TdHTMLAttributes<HTMLTableCellElement>) {
  return <td className={cn("px-4 py-3 align-middle text-foreground", className)} {...props} />;
}
