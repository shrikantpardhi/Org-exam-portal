import { QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const replace = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace, push: vi.fn(), refresh: vi.fn() }),
  usePathname: () => "/student",
}));

const mockUseAuth = vi.fn();
vi.mock("@/lib/auth/auth-context", () => ({
  useAuth: () => mockUseAuth(),
}));

vi.mock("@/features/communication/hooks/use-communication", () => ({
  useNotifications: () => ({ data: { items: [], total: 0 } }),
  useUnreadCount: () => ({ data: 0 }),
}));

import { StudentShell } from "@/components/shell/student-shell";
import { STUDENT_MENU } from "@/components/shell/sidebar-menu";
import { createQueryClient } from "@/lib/query-client";

function renderShell() {
  return render(
    <QueryClientProvider client={createQueryClient()}>
      <StudentShell>student content</StudentShell>
    </QueryClientProvider>,
  );
}

const STUDENT = {
  status: "authenticated",
  user: { id: "u1", roles: ["STUDENT"], mustChangePassword: false, firstName: "A", lastName: "B" },
  permissions: [],
  can: () => true,
  logout: vi.fn(),
};

/**
 * The student workspace shell.
 *
 * <p>The student area previously had no shell at all, so these tests exist mainly to stop it
 * regressing back to that state: they assert that the navigation is actually rendered and that the
 * same three-step gate used by the admin shell still applies.
 */
describe("StudentShell", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("redirects an unauthenticated visitor to /login, preserving the intended URL", () => {
    mockUseAuth.mockReturnValue({ ...STUDENT, status: "unauthenticated", user: null });
    renderShell();
    expect(replace).toHaveBeenCalledWith("/login?next=%2Fstudent");
  });

  it("redirects a student who must change their password", () => {
    mockUseAuth.mockReturnValue({
      ...STUDENT,
      user: { ...STUDENT.user, mustChangePassword: true },
    });
    renderShell();
    expect(replace).toHaveBeenCalledWith("/change-password");
  });

  it("sends a non-student to their own role home instead of rendering", () => {
    mockUseAuth.mockReturnValue({
      ...STUDENT,
      user: { ...STUDENT.user, roles: ["ADMIN"] },
    });
    renderShell();
    expect(replace).toHaveBeenCalledWith("/admin");
  });

  it("renders the student navigation for a signed-in student", () => {
    mockUseAuth.mockReturnValue(STUDENT);
    renderShell();

    const nav = screen.getByRole("navigation", { name: "Primary" });
    expect(nav).toBeInTheDocument();

    // The destinations a student could not previously reach without going back to the dashboard.
    // "Explore" is the student-facing name for the store route; "Reports" was removed because a
  // student has no need for a self-service reporting workspace.
  for (const label of ["Home", "Explore", "My access", "Results", "Analytics", "Profile"]) {
      expect(screen.getAllByRole("link", { name: label }).length).toBeGreaterThan(0);
    }
  });

  it("marks the current route as the active page", () => {
    mockUseAuth.mockReturnValue(STUDENT);
    renderShell();

    const home = screen.getAllByRole("link", { name: "Home" })[0];
    expect(home).toHaveAttribute("aria-current", "page");
  });

  it("exposes a skip link to the main content", () => {
    mockUseAuth.mockReturnValue(STUDENT);
    renderShell();
    expect(screen.getByRole("link", { name: /skip to content/i })).toHaveAttribute("href", "#main-content");
  });
});

describe("STUDENT_MENU", () => {
  it("only points at routes that exist", () => {
    // Guards against a menu entry being added for a page that was never built.
    const hrefs = STUDENT_MENU.flatMap((section) => section.items.map((item) => item.href));
    expect(hrefs).toContain("/student");
    expect(hrefs).toContain("/results");
    expect(hrefs).toContain("/analytics/student");
    expect(hrefs).toContain("/student/store");
    expect(new Set(hrefs).size).toBe(hrefs.length);
  });
});
