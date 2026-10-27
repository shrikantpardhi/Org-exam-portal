import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const ORG_ID = "11111111-1111-1111-1111-111111111111";

vi.mock("next/navigation", () => ({
  // The admins route is the interesting one: the organization segment is not the last crumb, so it
  // is a link, and the label registered by the page has to replace the uuid in that link's text.
  usePathname: () => "/platform/organizations/" + ORG_ID + "/admins",
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock("@/lib/auth/auth-context", () => ({
  useAuth: () => ({
    status: "authenticated",
    user: { id: "u1", roles: ["SUPER_ADMIN"], mustChangePassword: false, firstName: "A", lastName: "B" },
    permissions: [],
    can: () => true,
    logout: vi.fn(),
  }),
}));

vi.mock("@/features/communication/hooks/use-communication", () => ({
  useNotifications: () => ({ data: { items: [], total: 0 } }),
  useUnreadCount: () => ({ data: 0 }),
}));

vi.mock("@/lib/api-client", () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiPut: vi.fn(),
  apiPatch: vi.fn(),
  apiDelete: vi.fn(),
  setUnauthorizedHandler: vi.fn(),
}));

import { apiGet } from "@/lib/api-client";
import { Breadcrumbs, BreadcrumbLabelsProvider, useBreadcrumbLabel } from "@/components/shell/breadcrumbs";
import { WorkspaceShell } from "@/components/shell/workspace-shell";
import { OrganizationAdminsScreen } from "@/features/platform/components/organization-admins-screen";

const mockedGet = vi.mocked(apiGet);

function organization(name: string) {
  return {
    id: ORG_ID,
    code: "DEFAULT",
    name,
    status: "ACTIVE",
    description: null,
    adminCount: 0,
    userCount: 0,
    addressLine1: null,
    addressLine2: null,
    city: null,
    stateRegion: null,
    postalCode: null,
    country: null,
    contactEmail: null,
    contactPhone: null,
    websiteUrl: null,
    logoUrl: null,
    primaryAdminUserId: null,
    primaryAdminEmail: null,
  };
}

/** A page that registers its resource's name, the way a screen does. */
function RegisterLabel({ label }: { label: string | null }) {
  useBreadcrumbLabel(ORG_ID, label);
  return null;
}

function renderTrail(ui: ReactNode) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("breadcrumb labels", () => {
  it("humanises a segment that no label covers", () => {
    renderTrail(
      <Breadcrumbs root={{ label: "Platform", href: "/platform" }} />,
    );

    // No provider here at all: a standalone trail must still render rather than throw.
    const nav = screen.getByRole("navigation", { name: "Breadcrumb" });
    expect(within(nav).getByText("Organizations")).toBeInTheDocument();
    expect(within(nav).getByText("Admins")).toHaveAttribute("aria-current", "page");
    // The uuid is still humanised — that is exactly what a label is for.
    expect(within(nav).getByText("11111111 1111 1111 1111 111111111111")).toBeInTheDocument();
  });

  it("renders a supplied label instead of the humanised segment and keeps every href", () => {
    renderTrail(
      <Breadcrumbs
        root={{ label: "Platform", href: "/platform" }}
        labels={{ [ORG_ID]: "Default Organization" }}
      />,
    );

    const nav = screen.getByRole("navigation", { name: "Breadcrumb" });
    const organizationLink = within(nav).getByRole("link", { name: "Default Organization" });
    // The text changed; the href did not.
    expect(organizationLink).toHaveAttribute("href", "/platform/organizations/" + ORG_ID);
    expect(within(nav).queryByText(/11111111/)).toBeNull();
    // The remaining crumbs and the root link are untouched.
    expect(within(nav).getByRole("link", { name: "Platform" })).toHaveAttribute("href", "/platform");
    expect(within(nav).getByText("Admins")).toHaveAttribute("aria-current", "page");
  });

  it("registers a label through the provider and clears it on unmount", () => {
    const { rerender } = renderTrail(
      <BreadcrumbLabelsProvider>
        <Breadcrumbs root={{ label: "Platform", href: "/platform" }} />
        <RegisterLabel label="Default Organization" />
      </BreadcrumbLabelsProvider>,
    );

    const nav = () => screen.getByRole("navigation", { name: "Breadcrumb" });
    expect(within(nav()).getByRole("link", { name: "Default Organization" })).toBeInTheDocument();

    // A null label unregisters: while a resource is loading the trail keeps the raw segment rather
    // than inventing a name.
    rerender(
      <BreadcrumbLabelsProvider>
        <Breadcrumbs root={{ label: "Platform", href: "/platform" }} />
        <RegisterLabel label={null} />
      </BreadcrumbLabelsProvider>,
    );
    expect(within(nav()).queryByText("Default Organization")).toBeNull();
    expect(within(nav()).getByText("11111111 1111 1111 1111 111111111111")).toBeInTheDocument();
  });

  it("shows the organization's name, not its id, when the page registers it in the shell", async () => {
    mockedGet.mockImplementation((url: string) => {
      if (url.endsWith("/admins")) {
        return Promise.resolve({
          data: [],
          message: "ok",
          pagination: { page: 0, size: 10, totalElements: 0, totalPages: 1 },
        });
      }
      return Promise.resolve({ data: organization("Default Organization"), message: "ok" });
    });

    renderTrail(
      <WorkspaceShell home="/platform" menu={[]} homeLabel="Platform">
        <OrganizationAdminsScreen organizationId={ORG_ID} />
      </WorkspaceShell>,
    );

    const nav = await screen.findByRole("navigation", { name: "Breadcrumb" });
    // The page fetched the organization; the trail picks the name up without any prop drilling.
    const organizationLink = await within(nav).findByRole("link", { name: "Default Organization" });
    expect(organizationLink).toHaveAttribute("href", "/platform/organizations/" + ORG_ID);
    expect(within(nav).queryByText(/11111111/)).toBeNull();
    expect(within(nav).getByText("Admins")).toHaveAttribute("aria-current", "page");
  });
});
