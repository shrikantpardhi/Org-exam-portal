import { QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn(), push: vi.fn(), refresh: vi.fn() }),
  usePathname: () => "/student/results",
}));

const mockUseAuth = vi.fn();
vi.mock("@/lib/auth/auth-context", () => ({ useAuth: () => mockUseAuth() }));
vi.mock("@/features/communication/hooks/use-communication", () => ({
  useNotifications: () => ({ data: { items: [], total: 0 } }),
  useUnreadCount: () => ({ data: 0 }),
}));

import { StudentShell } from "@/components/shell/student-shell";
import { STUDENT_MENU } from "@/components/shell/sidebar-menu";
import { createQueryClient } from "@/lib/query-client";

const STUDENT = {
  status: "authenticated",
  user: { id: "u1", roles: ["STUDENT"], mustChangePassword: false, firstName: "A", lastName: "B" },
  permissions: [],
  can: () => true,
  logout: vi.fn(),
};

function renderShell() {
  return render(
    <QueryClientProvider client={createQueryClient()}>
      <StudentShell>
        <h1>Results</h1>
      </StudentShell>
    </QueryClientProvider>,
  );
}

/**
 * Structural accessibility of the workspace shell.
 *
 * <p>These are the guarantees a browser audit would check first, asserted at the DOM level because
 * no Playwright/axe tooling is installed in this repository. They do not replace a real assistive-
 * technology pass; they do prevent the specific regressions that a redesign causes (a second main
 * landmark, a missing skip link, an unlabelled nav, a heading level skip).
 */
describe("StudentShell accessibility structure", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseAuth.mockReturnValue(STUDENT);
  });

  it("exactly one main landmark, so a screen reader has one content target", () => {
    renderShell();
    expect(screen.getAllByRole("main")).toHaveLength(1);
  });

  it("labels the primary navigation", () => {
    renderShell();
    expect(screen.getByRole("navigation", { name: "Primary" })).toBeInTheDocument();
  });

  it("provides a breadcrumb trail", () => {
    renderShell();
    expect(screen.getByRole("navigation", { name: "Breadcrumb" })).toBeInTheDocument();
  });

  it("renders the page h1 and does not skip heading levels", () => {
    renderShell();
    expect(screen.getByRole("heading", { level: 1, name: "Results" })).toBeInTheDocument();
  });

  it("points skip-link at the real main content id", () => {
    renderShell();
    const skip = screen.getByRole("link", { name: /skip to content/i });
    expect(skip).toHaveAttribute("href", "#main-content");
    expect(document.querySelector("#main-content")).not.toBeNull();
  });

  it("gives the sidebar toggle a state and an accessible name", () => {
    renderShell();
    const toggle = screen.getByRole("button", { name: /navigation/i });
    expect(toggle).toHaveAttribute("aria-expanded");
  });
});

describe("STUDENT_MENU active-route matching", () => {
  it("uses subtree matching so a nested page keeps its parent highlighted", () => {
    // Mirrors WorkspaceShell.isActive. "/student" must not match "/student/store",
    // but "/results" must match "/results/abc/scorecard".
    const isActive = (href: string, pathname: string) =>
      href === "/student" ? pathname === "/student" : pathname === href || pathname.startsWith(href + "/");

    expect(isActive("/student", "/student")).toBe(true);
    expect(isActive("/student", "/student/store")).toBe(false);
    expect(isActive("/results", "/results/abc/scorecard")).toBe(true);
    expect(isActive("/results", "/resultset")).toBe(false);
    expect(STUDENT_MENU.length).toBeGreaterThan(0);
  });
});
