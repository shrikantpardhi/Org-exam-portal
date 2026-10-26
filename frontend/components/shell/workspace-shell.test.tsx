import { QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

vi.mock("next/navigation", () => ({
  usePathname: () => "/admin/commerce",
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn() }),
}));

// The shell reads the signed-in user for its identity block; supplying it directly keeps this test
// about landmarks rather than about authentication.
vi.mock("@/lib/auth/auth-context", () => ({
  useAuth: () => ({
    status: "authenticated",
    user: { id: "u1", roles: ["ADMIN"], mustChangePassword: false, firstName: "A", lastName: "B" },
    permissions: [],
    can: () => true,
    logout: vi.fn(),
  }),
}));

import { WorkspaceShell } from "@/components/shell/workspace-shell";
import { createQueryClient } from "@/lib/query-client";

/**
 * A workspace screen must expose exactly one main landmark.
 *
 * <p>Several administrative pages kept the page-level <main> they had from when they lived outside
 * the shell. Nested inside WorkspaceShell that produced two main landmarks, which is ambiguous for
 * screen-reader landmark navigation and invisible to type-check, lint, and every other test. This
 * pins the shell to exactly one.
 */
describe("WorkspaceShell landmarks", () => {
  it("renders exactly one main landmark for page content", () => {
    const { container } = render(
      <QueryClientProvider client={createQueryClient()}>
        <WorkspaceShell home="/admin" menu={[]} homeLabel="Admin">
          <div>page content</div>
        </WorkspaceShell>
      </QueryClientProvider>,
    );
    expect(container.querySelectorAll("main")).toHaveLength(1);
    expect(container.querySelector("main#main-content")).toBeTruthy();
  });
});
