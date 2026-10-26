"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

function humanize(segment: string): string {
  return segment.charAt(0).toUpperCase() + segment.slice(1).replace(/-/g, " ");
}

/** A breadcrumb root. Generalised so the student workspace can use the same trail. */
export type BreadcrumbRoot = { label: string; href: string };

/**
 * Route breadcrumbs ({@code docs/frontend/navigation.md}): root → resource → detail. The last crumb is
 * the current page and is not a link.
 *
 * <p>The root is described by `{ label, href }` rather than a hard-coded "Platform"/"Admin" union, so
 * adding the student workspace did not require widening a union that each caller had to know about.
 */
export function Breadcrumbs({
  root,
  homeLabel,
}: {
  /** Preferred form: the root label and link. */
  root?: BreadcrumbRoot;
  /** Backwards-compatible shorthand for the admin/platform roots. */
  homeLabel?: "Platform" | "Admin";
}) {
  const pathname = usePathname();
  const resolved: BreadcrumbRoot =
    root ??
    (homeLabel === "Platform"
      ? { label: "Platform", href: "/platform" }
      : { label: "Admin", href: "/admin" });

  const segments = pathname.split("/").filter(Boolean);
  const rest = segments.slice(1); // drop the leading "platform" / "admin" / "student"

  if (rest.length === 0) {
    return (
      <nav aria-label="Breadcrumb" className="mb-4 text-sm text-muted-foreground">
        <span aria-current="page">{resolved.label}</span>
      </nav>
    );
  }

  return (
    <nav aria-label="Breadcrumb" className="mb-4 flex flex-wrap items-center gap-1 text-sm text-muted-foreground">
      <Link href={resolved.href} className="transition-colors hover:text-foreground">
        {resolved.label}
      </Link>
      {rest.map((segment, index) => {
        const last = index === rest.length - 1;
        const href = `${resolved.href}/${rest.slice(0, index + 1).join("/")}`;
        return (
          <span key={index} className="flex items-center gap-1">
            <span aria-hidden className="text-border">
              /
            </span>
            {last ? (
              <span aria-current="page" className="font-medium text-foreground">
                {humanize(segment)}
              </span>
            ) : (
              <Link href={href} className="transition-colors hover:text-foreground">
                {humanize(segment)}
              </Link>
            )}
          </span>
        );
      })}
    </nav>
  );
}
