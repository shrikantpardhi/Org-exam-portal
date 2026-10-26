"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useId, useRef, useState, type ReactNode } from "react";
import { Menu } from "lucide-react";

import { AvatarMenu } from "./avatar-menu";
import { Breadcrumbs } from "./breadcrumbs";
import { GlobalSearch } from "./global-search";
import { NotificationBellMenu } from "./notification-bell-menu";
import type { ShellMenuSection } from "./sidebar-menu";

/** Sidebar width, in one place. Previously duplicated as `w-[264px]` / `md:pl-[264px]` / `md:left-[264px]`. */
const SIDEBAR_WIDTH = "264px";
const SIDEBAR_COLLAPSED = "56px";

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
  contextLabel,
  actions,
  children,
}: {
  home: string;
  menu: ShellMenuSection[];
  /** Breadcrumb root label, e.g. "Admin". */
  homeLabel: string;
  brandLabel?: string;
  /** Shown next to the brand, e.g. the organization name. */
  contextLabel?: string | null;
  /** Extra header actions, rendered before the notification bell. */
  actions?: ReactNode;
  children: ReactNode;
}) {
  const pathname = usePathname();
  const [collapsed, setCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const drawerId = useId();
  const toggleRef = useRef<HTMLButtonElement>(null);

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
    <nav className="flex h-full flex-col gap-6 overflow-y-auto p-3" aria-label="Primary">
      {menu.map((section, sectionIndex) => (
        <div key={sectionIndex} className="space-y-1">
          {section.title && !isCollapsed ? (
            <p className="px-3 pb-1 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
              {section.title}
            </p>
          ) : null}
          {section.items.map((item) => {
            const active = isActive(item.href);
            const Icon = item.icon;
            const classes =
              "flex items-center rounded-md text-sm transition-colors " +
              (active
                ? "bg-primary-subtle font-medium text-primary "
                : "text-muted-foreground hover:bg-muted hover:text-foreground ") +
              (item.disabled ? "cursor-not-allowed opacity-50 " : "") +
              (isCollapsed ? "justify-center p-2 " : "gap-3 px-3 py-2 ");

            if (item.disabled) {
              return (
                <span
                  key={item.href}
                  aria-disabled="true"
                  title={isCollapsed ? item.label : undefined}
                  className={classes}
                >
                  <Icon className="h-5 w-5 shrink-0" aria-hidden />
                  {!isCollapsed ? <span>{item.label}</span> : null}
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
          <header
            className="fixed top-0 right-0 left-0 z-20 flex h-16 items-center gap-3 border-b border-border bg-surface px-4"
            style={{ left: "0" }}
          >
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

            <Link href={home} className="hidden text-lg font-semibold tracking-tight text-foreground sm:block">
              {brandLabel}
            </Link>

            {contextLabel ? (
              <span className="hidden text-sm text-muted-foreground md:inline">{contextLabel}</span>
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
              <Breadcrumbs root={{ label: homeLabel, href: home }} />
              {children}
            </div>
          </main>
        </div>
      </div>
    </div>
  );
}
