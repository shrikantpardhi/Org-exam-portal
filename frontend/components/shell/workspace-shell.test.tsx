import { QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("next/navigation", () => ({
  usePathname: () => "/admin/commerce",
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn() }),
}));

// The shell reads the signed-in user for its identity block; supplying it directly keeps these tests
// about the chrome rather than about authentication.
vi.mock("@/lib/auth/auth-context", () => ({
  useAuth: () => ({
    status: "authenticated",
    user: { id: "u1", roles: ["ADMIN"], mustChangePassword: false, firstName: "A", lastName: "B" },
    permissions: [],
    can: () => true,
    logout: vi.fn(),
  }),
}));

vi.mock("@/features/communication/hooks/use-communication", () => ({
  useNotifications: () => ({ data: { items: [], total: 0 } }),
  useUnreadCount: () => ({ data: 0 }),
}));

import {
  ADMIN_MENU,
  STUDENT_MENU,
  SUPER_ADMIN_MENU,
  type ShellMenuSection,
} from "@/components/shell/sidebar-menu";
import {
  SIDEBAR_COLLAPSED_STORAGE_KEY,
  WorkspaceShell,
} from "@/components/shell/workspace-shell";
import { createQueryClient } from "@/lib/query-client";

function renderShell({
  menu = [] as ShellMenuSection[],
  brandLabel,
  logoUrl,
}: {
  menu?: ShellMenuSection[];
  brandLabel?: string;
  logoUrl?: string;
} = {}) {
  return render(
    <QueryClientProvider client={createQueryClient()}>
      <WorkspaceShell
        home="/admin"
        menu={menu}
        homeLabel="Admin"
        brandLabel={brandLabel}
        logoUrl={logoUrl}
      >
        <div>page content</div>
      </WorkspaceShell>
    </QueryClientProvider>,
  );
}

/**
 * The desktop sidebar aside. The mobile drawer is also an `<aside>`, but it is the one carrying
 * `role="dialog"`, so this picks the always-mounted desktop column.
 */
function sidebar(container: HTMLElement): HTMLElement {
  const asides = Array.from(container.querySelectorAll("aside")).filter(
    (aside) => aside.getAttribute("role") !== "dialog",
  );
  expect(asides).toHaveLength(1);
  return asides[0] as HTMLElement;
}

/**
 * jsdom implements no `matchMedia`, which the toggle uses to choose between the desktop collapse and
 * the mobile drawer, so each test states which side of the breakpoint it is on.
 */
function stubMatchMedia(matches: boolean) {
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })) as unknown as typeof window.matchMedia;
}

beforeEach(() => {
  window.localStorage.clear();
  stubMatchMedia(false);
});

/**
 * The shell chrome.
 *
 * <p>Structure tests: a workspace screen must expose exactly one main landmark. Several
 * administrative pages kept the page-level <main> they had from when they lived outside the shell;
 * nested inside WorkspaceShell that produced two main landmarks, which is ambiguous for screen-reader
 * landmark navigation and invisible to type-check, lint, and every other test.
 *
 * <p>Behaviour tests: the collapsed sidebar is a per-user preference, and a collapsed sidebar that
 * hides the only home link leaves a keyboard or screen-reader user with no way back to the role home.
 * Both are asserted here because neither shows up in a type-check.
 */
describe("WorkspaceShell landmarks", () => {
  it("renders exactly one main landmark for page content", () => {
    const { container } = renderShell();
    expect(container.querySelectorAll("main")).toHaveLength(1);
    expect(container.querySelector("main#main-content")).toBeTruthy();
  });
});

describe("WorkspaceShell sidebar collapse", () => {
  it("is expanded by default and records the default", () => {
    const { container } = renderShell();
    expect(sidebar(container)).toHaveStyle({ width: "264px" });
    expect(window.localStorage.getItem(SIDEBAR_COLLAPSED_STORAGE_KEY)).toBe("false");
  });

  it("persists the collapsed choice and restores it after a remount", async () => {
    const user = userEvent.setup();
    const first = renderShell();

    await user.click(screen.getByRole("button", { name: /collapse navigation/i }));

    expect(sidebar(first.container)).toHaveStyle({ width: "56px" });
    expect(window.localStorage.getItem(SIDEBAR_COLLAPSED_STORAGE_KEY)).toBe("true");

    // A remount is what a route change or a page refresh does to this component.
    first.unmount();
    const second = renderShell();

    expect(sidebar(second.container)).toHaveStyle({ width: "56px" });
    expect(screen.getByRole("button", { name: /expand navigation/i })).toBeInTheDocument();

    // Toggling back is persisted too, so the preference is symmetric.
    await user.click(screen.getByRole("button", { name: /expand navigation/i }));
    expect(sidebar(second.container)).toHaveStyle({ width: "264px" });
    expect(window.localStorage.getItem(SIDEBAR_COLLAPSED_STORAGE_KEY)).toBe("false");
  });

  it("restores a stored preference without overwriting it before it is read", () => {
    window.localStorage.setItem(SIDEBAR_COLLAPSED_STORAGE_KEY, "true");

    const { container } = renderShell();

    expect(sidebar(container)).toHaveStyle({ width: "56px" });
    // The mount-time read must win: a mount-time write of the default would erase the preference.
    expect(window.localStorage.getItem(SIDEBAR_COLLAPSED_STORAGE_KEY)).toBe("true");
  });

  it("keeps the mobile drawer behaviour on the small-screen branch of the toggle", async () => {
    stubMatchMedia(true);
    const user = userEvent.setup();
    const { container } = renderShell();

    await user.click(screen.getByRole("button", { name: /collapse navigation/i }));

    const drawer = screen.getByRole("dialog", { name: "Navigation" });
    expect(within(drawer).getByRole("navigation", { name: "Primary" })).toBeInTheDocument();
    // The desktop sidebar stays mounted at its expanded width; only the drawer opened.
    expect(sidebar(container)).toHaveStyle({ width: "264px" });
    // The drawer's own close control is the scrim button, labelled for assistive technology.
    expect(screen.getByRole("button", { name: "Close navigation" })).toBeInTheDocument();
  });
});

describe("WorkspaceShell sidebar brand", () => {
  it("shows a text mark derived from the brand in both states", async () => {
    const user = userEvent.setup();
    const { container } = renderShell();

    expect(within(sidebar(container)).getByText("EP")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /collapse navigation/i }));

    expect(sidebar(container)).toHaveStyle({ width: "56px" });
    // Collapsed, the label is gone but the mark remains, so the sidebar is never unbranded.
    expect(within(sidebar(container)).getByText("EP")).toBeInTheDocument();
    expect(within(sidebar(container)).queryByText("Exam Platform")).toBeNull();
  });

  it("keeps a named home link when collapsed", async () => {
    const user = userEvent.setup();
    const { container } = renderShell();

    await user.click(screen.getByRole("button", { name: /collapse navigation/i }));

    const home = within(sidebar(container)).getByRole("link", { name: "Exam Platform" });
    expect(home).toHaveAttribute("href", "/admin");
    expect(home).toHaveAttribute("title", "Exam Platform");
  });

  it("renders an optional logo as a decorative image inside the named link", () => {
    const { container } = renderShell({ logoUrl: "/brand-mark.png" });

    const home = within(sidebar(container)).getByRole("link", { name: "Exam Platform" });
    const image = home.querySelector("img");
    expect(image).not.toBeNull();
    expect(image).toHaveAttribute("src", "/brand-mark.png");
    // Decorative: the link already carries the accessible name, so an alt text would duplicate it.
    expect(image).toHaveAttribute("alt", "");
  });

  it("uses the supplied brand label for the mark and the accessible name", () => {
    const { container } = renderShell({ brandLabel: "Acme Exams" });

    const home = within(sidebar(container)).getByRole("link", { name: "Acme Exams" });
    expect(home).toBeInTheDocument();
    expect(within(sidebar(container)).getByText("AE")).toBeInTheDocument();
  });
});

describe("WorkspaceShell disabled entries", () => {
  it("renders a route-less entry as a non-link that explains itself", () => {
    const { container } = renderShell({ menu: SUPER_ADMIN_MENU });

    // No anchor exists for a route that was never built — not even inside the collapsed sidebar.
    expect(container.querySelector('a[href="/platform/multi-org"]')).toBeNull();
    expect(screen.queryByRole("link", { name: /multi-org/i })).toBeNull();

    const label = screen.getByText("Multi-org");
    const entry = label.closest("span[aria-disabled='true']");
    expect(entry).not.toBeNull();
    expect(entry?.closest("a")).toBeNull();
    expect(entry).toHaveAttribute("title");
    expect(entry?.getAttribute("title")).toMatch(/ADR-0033/);
    expect(entry?.getAttribute("title")).toMatch(/V2/);
    // The visible "V2" tag states the same thing without relying on a tooltip.
    expect(within(entry as HTMLElement).getByText("V2")).toBeInTheDocument();
  });
});

describe("sidebar menus", () => {
  it("no longer offers a Lookups entry in either administrative menu", () => {
    for (const menu of [SUPER_ADMIN_MENU, ADMIN_MENU]) {
      const items = menu.flatMap((section) => section.items);
      expect(items.map((item) => item.label)).not.toContain("Lookups");
      expect(items.some((item) => item.href?.includes("lookups"))).toBe(false);
    }
  });

  it("gives every usable entry a route and every unusable one a reason", () => {
    const items = [...SUPER_ADMIN_MENU, ...ADMIN_MENU, ...STUDENT_MENU].flatMap(
      (section) => section.items,
    );
    for (const item of items) {
      if (item.disabled) {
        expect(item.href).toBeUndefined();
        expect(item.note).toBeTruthy();
      } else {
        expect(item.href).toMatch(/^\//);
      }
    }
  });

  it("keeps multi-org listed once, as the only disabled platform entry", () => {
    const multiOrg = SUPER_ADMIN_MENU.flatMap((section) => section.items).filter(
      (item) => item.label === "Multi-org",
    );
    expect(multiOrg).toHaveLength(1);
    const [entry] = multiOrg;
    expect(entry?.disabled).toBe(true);
    expect(entry?.badge).toBe("V2");
  });
});
