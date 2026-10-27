"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";

function humanize(segment: string): string {
  return segment.charAt(0).toUpperCase() + segment.slice(1).replace(/-/g, " ");
}

/** A breadcrumb root. Generalised so the student workspace can use the same trail. */
export type BreadcrumbRoot = { label: string; href: string };

/** Labels registered by the mounted page, keyed by the raw route segment they replace. */
export type BreadcrumbLabels = Record<string, string>;

interface BreadcrumbLabelsContextValue {
  labels: BreadcrumbLabels;
  setLabel: (segmentValue: string, label: string) => void;
  unsetLabel: (segmentValue: string) => void;
}

/** Shared provider state, so the labels do not have to be prop-drilled through the layout. */
const BreadcrumbLabelsContext = createContext<BreadcrumbLabelsContextValue | null>(null);

/** A stable empty map: a screen rendered outside a provider must not create a new object per render. */
const NO_LABELS: BreadcrumbLabels = {};

/**
 * Holds the labels the current page wants the breadcrumb trail to show.
 *
 * <p>The trail is derived from {@code usePathname()}, which knows the route but not what the resource
 * is called: a detail route carries a UUID, and humanising it renders
 * "11111111 1111 1111 1111 111111111111". A page that has fetched the resource can name it, so it
 * registers the name here and the trail stays on the layout side, where it belongs.
 *
 * <p>The setter is guarded: registering the same value twice returns the previous state, so an
 * effect that runs after every render cannot loop through a context identity change.
 */
export function BreadcrumbLabelsProvider({ children }: { children: ReactNode }) {
  const [labels, setLabels] = useState<BreadcrumbLabels>(NO_LABELS);

  const setLabel = useCallback((segmentValue: string, label: string) => {
    setLabels((previous) =>
      previous[segmentValue] === label ? previous : { ...previous, [segmentValue]: label },
    );
  }, []);

  const unsetLabel = useCallback((segmentValue: string) => {
    setLabels((previous) => {
      if (!(segmentValue in previous)) {
        return previous;
      }
      const next = { ...previous };
      delete next[segmentValue];
      return next;
    });
  }, []);

  // Memoised so a label change re-renders only the consumers that read it, and so the effect in
  // useBreadcrumbLabel sees stable setters rather than a fresh object on every render.
  const value = useMemo(() => ({ labels, setLabel, unsetLabel }), [labels, setLabel, unsetLabel]);

  return (
    <BreadcrumbLabelsContext.Provider value={value}>{children}</BreadcrumbLabelsContext.Provider>
  );
}

/** The labels currently registered. An empty map outside a provider, so a screen can render alone. */
export function useBreadcrumbLabels(): BreadcrumbLabels {
  return useContext(BreadcrumbLabelsContext)?.labels ?? NO_LABELS;
}

/**
 * Registers the display name of one route segment for as long as the calling component is mounted.
 *
 * <p>A null or undefined label registers nothing: while the resource is still loading the trail keeps
 * showing the raw segment, which is better than showing a fabricated name. Changing the label
 * replaces it; unmounting clears it, so the name never leaks into the next route's trail.
 */
export function useBreadcrumbLabel(segmentValue: string, label: string | null | undefined): void {
  const context = useContext(BreadcrumbLabelsContext);
  const setLabel = context?.setLabel;
  const unsetLabel = context?.unsetLabel;

  useEffect(() => {
    if (!segmentValue || !label || !setLabel || !unsetLabel) {
      return;
    }
    setLabel(segmentValue, label);
    return () => unsetLabel(segmentValue);
  }, [segmentValue, label, setLabel, unsetLabel]);
}

/**
 * Route breadcrumbs ({@code docs/frontend/navigation.md}): root → resource → detail. The last crumb is
 * the current page and is not a link.
 *
 * <p>The root is described by `{ label, href }` rather than a hard-coded "Platform"/"Admin" union, so
 * adding the student workspace did not require widening a union that each caller had to know about.
 *
 * <p>A segment's visible text is a registered label when the page supplied one (see
 * {@link useBreadcrumbLabel}); only the text changes. Every href is still derived from the raw
 * segments, so naming a resource can never break a link.
 */
export function Breadcrumbs({
  root,
  homeLabel,
  labels,
}: {
  /** Preferred form: the root label and link. */
  root?: BreadcrumbRoot;
  /** Backwards-compatible shorthand for the admin/platform roots. */
  homeLabel?: "Platform" | "Admin";
  /** Labels keyed by segment value. Overrides the ones registered through the provider. */
  labels?: BreadcrumbLabels;
}) {
  const pathname = usePathname();
  const registered = useBreadcrumbLabels();
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

  /** The prop form wins over the provider: a caller passing labels knows more than the context does. */
  const textFor = (segment: string) => labels?.[segment] ?? registered[segment] ?? humanize(segment);

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
                {textFor(segment)}
              </span>
            ) : (
              <Link href={href} className="transition-colors hover:text-foreground">
                {textFor(segment)}
              </Link>
            )}
          </span>
        );
      })}
    </nav>
  );
}
