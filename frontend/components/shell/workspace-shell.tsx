"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useId, useRef, useState, type ReactNode } from "react";
import { Menu } from "lucide-react";

import { AvatarMenu } from "./avatar-menu";
import { Breadcrumbs, BreadcrumbLabelsProvider } from "./breadcrumbs";
import { GlobalSearch } from "./global-search";
import { NotificationBellMenu } from "./notification-bell-menu";
import type { ShellMenuSection } from "./sidebar-menu";

/** Sidebar width, in one place. Previously duplicated as `w-[264px]` / `md:pl-[264px]` / `md:left-[264px]`. */
const SIDEBAR_WIDTH = "264px";
const SIDEBAR_COLLAPSED = "56px";

/**
 * Where the desktop sidebar's expanded/collapsed choice is remembered.
 *
 * <p>The value is read in an effect, never during render: the server cannot see `localStorage`, so
 * restoring during render would paint one width on the server and a different one on the client.
 */
export const SIDEBAR_COLLAPSED_STORAGE_KEY = "exam-platform.sidebar-collapsed";

function readStoredCollapsed(): boolean | null {
  try {
    const stored = window.localStorage.getItem(SIDEBAR_COLLAPSED_STORAGE_KEY);
    return stored === null ? null : stored === "true";
  } catch {
    // Storage can be unavailable (private mode, blocked third-party storage). The default applies.
    return null;
  }
}

function storeCollapsed(collapsed: boolean): void {
  try {
    window.localStorage.setItem(SIDEBAR_COLLAPSED_STORAGE_KEY, String(collapsed));
  } catch {
    // A remembered preference is a convenience; the sidebar still collapses for this visit.
  }
}

/** "Exam Platform" → "EP". A text mark, so the shell never has to invent a logo asset. */
function brandInitials(label: string): string {
  const initials = label
    .split(/\s+/)
    .filter(Boolean)
    .map((word) => word.charAt(0))
    .join("")
    .toUpperCase()
    .slice(0, 2);
  return initials || "EP";
}

/**
 * The sidebar's brand and home link.
 *
 * <p>Expanded, it shows the mark plus the label; collapsed at 56 px it shows the mark alone. The link
 * keeps an `aria-label` in both states, so the accessible name is the brand rather than the glyph,
 * and a collapsed sidebar still offers a labelled way home.
 */
function SidebarBrand({
  home,
  brandLabel,
  logoUrl,
  collapsed,
}: {
  home: string;
  brandLabel: string;
  logoUrl?: string | null;
  collapsed: boolean;
}) {
  const mark = brandInitials(brandLabel);

  return (
    <div className={collapsed ? "flex justify-center px-2 pt-3" : "px-3 pt-3"}>
      <Link
        href={home}
        aria-label={brandLabel}
        title={collapsed ? brandLabel : undefined}
        className={
          collapsed
            ? "flex h-8 w-8 items-center justify-center rounded-md bg-primary text-xs font-semibold text-primary-foreground"
            : "flex items-center gap-2 rounded-md text-base font-semibold tracking-tight text-foreground"
        }
      >
        {logoUrl ? (
          // The logo URL is runtime configuration, not a bundled asset Next can optimize, so a plain
          // <img> is the correct element here. It is decorative: the link already carries the name.
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={logoUrl}
            alt=""
            aria-hidden
            className={collapsed ? "h-8 w-8 rounded-md object-contain" : "h-7 w-7 rounded-md object-contain"}
          />
        ) : (
          <span
            aria-hidden
            className={
              collapsed
                ? undefined
                : "flex h-7 w-7 items-center justify-center rounded-md bg-primary text-xs font-semibold text-primary-foreground"
            }
          >
            {mark}
          </span>
        )}
        {collapsed ? null : <span>{brandLabel}</span>}
      </Link>
    </div>
  );
}

/**
 * The chrome shared by every authenticated workspace: sidebar, header, breadcrumbs, content.
 *
 * <p>Extracted from `AppShell` because the student workspace had no shell at all — ten pages each
 * rendered their own `<main>` and a student could not move between results, store and notifications.
 * Rather than duplicate ~190 lines, role-specific shells pass a menu and a home path to this.
 *
 * <p>Hidden menu items are a UX affordance; the backend remains authoritative (§63).
 */
export function WorkspaceShell({
  home,
  menu,
  homeLabel,
  brandLabel = "Exam Platform",
  logoUrl,
  contextLabel,
  actions,
  children,
}: {
  home: string;
  menu: ShellMenuSection[];
  /** Breadcrumb root label, e.g. "Admin". */
  homeLabel: string;
  brandLabel?: string;
  /** Optional organization logo for the sidebar brand. Rendered as a decorative image. */
  logoUrl?: string | null;
  /** Shown next to the brand, e.g. the organization name. */
  contextLabel?: string | null;
  /** Extra header actions, rendered before the notification bell. */
  actions?: ReactNode;
  children: ReactNode;
}) {
  const pathname = usePathname();
  const [collapsed, setCollapsed] = useState(false);
  // Whether the stored preference has been applied yet. The write effect must not run before the
  // read does, or the first render would overwrite the saved value with the default.
  const [preferenceLoaded, setPreferenceLoaded] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const drawerId = useId();
  const toggleRef = useRef<HTMLButtonElement>(null);

  // Restore after mount: the server rendered the default, so this is the first place the stored
  // choice can be applied without a hydration mismatch.
  useEffect(() => {
    const stored = readStoredCollapsed();
    if (stored !== null) setCollapsed(stored);
    setPreferenceLoaded(true);
  }, []);

  // Persist on change, but only once the stored value has been read back into state.
  useEffect(() => {
    if (!preferenceLoaded) return;
    storeCollapsed(collapsed);
  }, [collapsed, preferenceLoaded]);

  // Close the drawer on navigation: a drawer left open over a new page is a trap on touch devices.
  useEffect(() => {
    setMobileOpen(false);
  }, [pathname]);

  // Escape closes and returns focus to the control that opened it, so a keyboard user is not
  // dropped at the top of the document.
  useEffect(() => {
    if (!mobileOpen) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setMobileOpen(false);
        toggleRef.current?.focus();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [mobileOpen]);

  function isActive(href: string) {
    // The home route matches exactly; every other route matches its own subtree, so a nested page
    // keeps its parent highlighted.
    return href === home ? pathname === home : pathname === href || pathname.startsWith(href + "/");
  }

  const sidebar = (isCollapsed: boolean) => (
    <div className="flex h-full flex-col">
      <SidebarBrand
        home={home}
        brandLabel={brandLabel}
        logoUrl={logoUrl}
        collapsed={isCollapsed}
      />
      <nav
        className="flex min-h-0 flex-1 flex-col gap-6 overflow-y-auto p-3"
        aria-label="Primary"
      >
        {menu.map((section, sectionIndex) => (
          <div key={sectionIndex} className="space-y-1">
            {section.title && !isCollapsed ? (
              <p className="px-3 pb-1 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                {section.title}
              </p>
            ) : null}
            {section.items.map((item) => {
              const Icon = item.icon;
              const active = item.href ? isActive(item.href) : false;
              const classes =
                "flex items-center rounded-md text-sm transition-colors " +
                (active
                  ? "bg-primary-subtle font-medium text-primary "
                  : "text-muted-foreground hover:bg-muted hover:text-foreground ") +
                (item.disabled ? "cursor-not-allowed opacity-50 " : "") +
                (isCollapsed ? "justify-center p-2 " : "gap-3 px-3 py-2 ");

              // A disabled entry is deliberately NOT a link: an item whose route does not exist must
              // never render a dead anchor. It states why it is unavailable instead.
              if (item.disabled || !item.href) {
                return (
                  <span
                    key={item.href ?? item.label}
                    aria-disabled="true"
                    title={item.note ?? (isCollapsed ? item.label : undefined)}
                    className={classes}
                  >
                    <Icon className="h-5 w-5 shrink-0" aria-hidden />
                    {!isCollapsed ? (
                      <span className="flex flex-1 items-center justify-between gap-2">
                        {item.label}
                        {item.badge ? (
                          <span className="rounded bg-muted px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">
                            {item.badge}
                          </span>
                        ) : null}
                      </span>
                    ) : null}
                    {item.note ? <span className="sr-only">{item.note}</span> : null}
                  </span>
                );
              }

              return (
                <Link
                  key={item.href}
                  href={item.href}
                  aria-current={active ? "page" : undefined}
                  title={isCollapsed ? item.label : undefined}
                  className={classes}
                >
                  <Icon className="h-5 w-5 shrink-0" aria-hidden />
                  {!isCollapsed ? <span>{item.label}</span> : null}
                </Link>
              );
            })}
          </div>
        ))}
      </nav>
    </div>
  );

  return (
    <div className="min-h-screen bg-background">
      <a href="#main-content" className="skip-link">
        Skip to content
      </a>

      {/* Desktop sidebar */}
      <aside
        className="fixed inset-y-0 left-0 z-30 hidden border-r border-border bg-surface transition-[width] duration-150 md:block"
        style={{ width: collapsed ? SIDEBAR_COLLAPSED : SIDEBAR_WIDTH }}
      >
        {sidebar(collapsed)}
      </aside>

      {/* Mobile drawer: a modal dialog, so focus and announcement behave like one. */}
      {mobileOpen ? (
        <div className="md:hidden">
          <button
            type="button"
            aria-label="Close navigation"
            className="fixed inset-0 z-40 bg-foreground/40"
            onClick={() => setMobileOpen(false)}
          />
          <aside
            id={drawerId}
            role="dialog"
            aria-modal="true"
            aria-label="Navigation"
            className="fixed inset-y-0 left-0 z-50 border-r border-border bg-surface"
            style={{ width: SIDEBAR_WIDTH }}
          >
            {sidebar(false)}
          </aside>
        </div>
      ) : null}

      <div
        className="transition-[padding] duration-150"
        style={{ "--sidebar": collapsed ? SIDEBAR_COLLAPSED : SIDEBAR_WIDTH } as React.CSSProperties}
      >
        <div className="md:pl-[var(--sidebar)]">
          {/*
            The header spans the content region to the right of the sidebar ("Header: 64 px tall,
            fixed top, spans the remaining width"), which keeps the collapse toggle reachable: it
            sits beside the sidebar, not underneath it.
          */}
          <header className="fixed top-0 right-0 left-0 z-20 flex h-16 items-center gap-3 border-b border-border bg-surface px-4 transition-[left] duration-150 md:left-[var(--sidebar)]">
            <button
              ref={toggleRef}
              type="button"
              onClick={() => {
                if (window.matchMedia("(max-width: 767px)").matches) {
                  setMobileOpen(true);
                } else {
                  setCollapsed((value) => !value);
                }
              }}
              aria-label={collapsed ? "Expand navigation" : "Collapse navigation"}
              aria-expanded={mobileOpen}
              aria-controls={mobileOpen ? drawerId : undefined}
              className="rounded-md p-2 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
            >
              <Menu className="h-5 w-5" aria-hidden />
            </button>

            {/* Below 768 px the sidebar is a closed drawer, so the header carries the home link
                instead. The sidebar owns it at md and up, so this one is hidden there. */}
            <Link
              href={home}
              aria-label={brandLabel}
              className="flex items-center gap-2 text-sm font-semibold tracking-tight text-foreground md:hidden"
            >
              <span
                aria-hidden
                className="flex h-7 w-7 items-center justify-center rounded-md bg-primary text-xs font-semibold text-primary-foreground"
              >
                {brandInitials(brandLabel)}
              </span>
            </Link>

            {contextLabel ? (
              <span className="hidden truncate text-sm text-muted-foreground md:inline">
                {contextLabel}
              </span>
            ) : null}

            <div className="ml-auto flex items-center gap-2">
              <GlobalSearch />
              {actions}
              <NotificationBellMenu />
              <AvatarMenu />
            </div>
          </header>

          <main id="main-content" className="pt-16">
            <div className="mx-auto w-full max-w-[1400px] px-4 py-6 md:px-6">
              {/*
                The provider wraps the trail and the page together: a screen registers the display
                name of the resource it fetched, and the breadcrumb above it reads that name instead
                of humanising a UUID. Both live inside this provider, so the label is available in
                the same render pass the page mounts in.
              */}
              <BreadcrumbLabelsProvider>
                <Breadcrumbs root={{ label: homeLabel, href: home }} />
                {children}
              </BreadcrumbLabelsProvider>
            </div>
          </main>
        </div>
      </div>
    </div>
  );
}
