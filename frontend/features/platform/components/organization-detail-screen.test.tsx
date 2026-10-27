import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/api-client", () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiPut: vi.fn(),
  apiPatch: vi.fn(),
  apiDelete: vi.fn(),
  setUnauthorizedHandler: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => "/platform/organizations/11111111-1111-1111-1111-111111111111",
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn() }),
}));

import { apiGet, apiPatch } from "@/lib/api-client";
import { Breadcrumbs, BreadcrumbLabelsProvider } from "@/components/shell/breadcrumbs";
import { OrganizationDetailScreen } from "@/features/platform/components/organization-detail-screen";

const mockedGet = vi.mocked(apiGet);
const mockedPatch = vi.mocked(apiPatch);

const ORG_ID = "11111111-1111-1111-1111-111111111111";

function organization(overrides: Record<string, unknown> = {}) {
  return {
    id: ORG_ID,
    code: "DEFAULT",
    name: "Default Organization",
    status: "ACTIVE",
    description: null,
    adminCount: 1,
    userCount: 4,
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
    primaryAdminUserId: "22222222-2222-2222-2222-222222222222",
    primaryAdminEmail: "primary@example.test",
    ...overrides,
  };
}

function mockOrganization(overrides: Record<string, unknown> = {}) {
  mockedGet.mockResolvedValue({ data: organization(overrides), message: "ok" });
}

function renderScreen() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <OrganizationDetailScreen organizationId={ORG_ID} />
    </QueryClientProvider>,
  );
}

/** The status dialog, opened by the button in the card header. */
async function openStatusDialog() {
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: "Change status" }));
  return { user, dialog: await screen.findByRole("dialog") };
}

beforeEach(() => {
  vi.clearAllMocks();
});

/**
 * The organization status control ({`PATCH /platform/organizations/{id}/status`}).
 *
 * <p>Suspending an organization locks every one of its users out, because the authentication service
 * refuses a login while the organization is not ACTIVE. The warning therefore has to appear on the
 * direction that causes it and only on that direction.
 */
describe("organization status control", () => {
  it("warns before moving away from ACTIVE", async () => {
    mockOrganization({ status: "ACTIVE" });
    renderScreen();

    const { user, dialog } = await openStatusDialog();
    // The dialog opens on the current value, so nothing is about to change yet.
    const select = within(dialog).getByLabelText("Status");
    expect(select).toHaveValue("ACTIVE");
    expect(within(dialog).queryByText(/will not be able to sign in/)).toBeNull();

    await user.selectOptions(select, "INACTIVE");

    expect(
      within(dialog).getByText(
        "Users of this organization will not be able to sign in while it is not active.",
      ),
    ).toBeInTheDocument();
  });

  it("does not warn when reactivating an INACTIVE organization", async () => {
    mockOrganization({ status: "INACTIVE" });
    renderScreen();

    const { user, dialog } = await openStatusDialog();
    const select = within(dialog).getByLabelText("Status");
    expect(select).toHaveValue("INACTIVE");
    expect(
      within(dialog).getByText(/its users cannot sign in\. Choosing ACTIVE restores access\./),
    ).toBeInTheDocument();

    await user.selectOptions(select, "ACTIVE");

    expect(within(dialog).queryByText(/will not be able to sign in/)).toBeNull();
  });

  it("submits the chosen status to the status endpoint and closes", async () => {
    mockOrganization({ status: "ACTIVE" });
    mockedPatch.mockResolvedValue({ data: organization({ status: "INACTIVE" }), message: "ok" });
    renderScreen();

    const { user, dialog } = await openStatusDialog();
    await user.selectOptions(within(dialog).getByLabelText("Status"), "INACTIVE");
    await user.click(within(dialog).getByRole("button", { name: "Change status" }));

    expect(mockedPatch).toHaveBeenCalledTimes(1);
    expect(mockedPatch).toHaveBeenCalledWith(
      "/platform/organizations/" + ORG_ID + "/status",
      { status: "INACTIVE" },
    );
    // The dialog closes on success so the badge underneath is what the operator looks at next.
    await screen.findByRole("button", { name: "Change status" });
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("keeps the dialog open and reports the refusal when the server rejects the change", async () => {
    mockOrganization({ status: "ACTIVE" });
    mockedPatch.mockRejectedValue(new Error("refused"));
    renderScreen();

    const { user, dialog } = await openStatusDialog();
    await user.selectOptions(within(dialog).getByLabelText("Status"), "INACTIVE");
    await user.click(within(dialog).getByRole("button", { name: "Change status" }));

    expect(
      await within(dialog).findByText(/The organization status could not be changed./),
    ).toBeInTheDocument();
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });

  it("registers the organization name for the breadcrumb trail", async () => {
    mockOrganization();
    render(
      <QueryClientProvider
        client={new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })}
      >
        <BreadcrumbLabelsProvider>
          <Breadcrumbs root={{ label: "Platform", href: "/platform" }} />
          <OrganizationDetailScreen organizationId={ORG_ID} />
        </BreadcrumbLabelsProvider>
      </QueryClientProvider>,
    );

    await screen.findByRole("heading", { name: "Default Organization" });
    // The trail registers the name the page fetched, so the uuid is not what the operator reads.
    const nav = screen.getByRole("navigation", { name: "Breadcrumb" });
    expect(await within(nav).findByText("Default Organization")).toBeInTheDocument();
    expect(within(nav).queryByText(/11111111/)).toBeNull();
  });
});


/**
 * The edit-profile dialog ({@code PATCH /platform/organizations/{id}}, ADR-0056).
 *
 * <p>The wire contract is a true PATCH: an omitted field is left unchanged and an empty string
 * clears it. The tests below pin exactly that, because sending the whole form instead would turn
 * every untouched input into a write, and would make an untouched empty input indistinguishable
 * from an operator deliberately clearing a stale phone number.
 */
describe("organization profile editing", () => {
  const seeded = {
    name: "Default Organization",
    addressLine1: "12 Example Street",
    city: "Bengaluru",
    country: "India",
    contactEmail: "office@example.edu",
    contactPhone: "+91 80 0000 0000",
    websiteUrl: "https://example.edu",
  };

  async function openEditDialog() {
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: "Edit profile" }));
    return { user, dialog: await screen.findByRole("dialog") };
  }

  it("opens seeded with the values the organization currently reports", async () => {
    mockOrganization(seeded);
    renderScreen();

    const { dialog } = await openEditDialog();

    expect(within(dialog).getByRole("heading", { name: "Edit organization profile" })).toBeInTheDocument();
    // Seeded, not blank: the operator corrects what exists instead of re-typing the profile.
    expect(within(dialog).getByLabelText("Name (required)")).toHaveValue("Default Organization");
    expect(within(dialog).getByLabelText("Address line 1")).toHaveValue("12 Example Street");
    expect(within(dialog).getByLabelText("City")).toHaveValue("Bengaluru");
    expect(within(dialog).getByLabelText("Country")).toHaveValue("India");
    expect(within(dialog).getByLabelText("Contact email")).toHaveValue("office@example.edu");
    // A column the organization never filled in seeds as an empty input, not as the text "null".
    expect(within(dialog).getByLabelText("Postal code")).toHaveValue("");
    // The code is immutable here: it is the stable handle and the endpoint refuses it.
    expect(within(dialog).queryByLabelText(/Code/)).toBeNull();
  });

  it("sends only the changed values to the organization route and closes", async () => {
    mockOrganization(seeded);
    mockedPatch.mockResolvedValue({ data: organization({ ...seeded, name: "Renamed Org" }), message: "ok" });
    renderScreen();

    const { user, dialog } = await openEditDialog();
    const name = within(dialog).getByLabelText("Name (required)");
    await user.clear(name);
    await user.type(name, "Renamed Org");
    const city = within(dialog).getByLabelText("City");
    await user.clear(city);
    await user.type(city, "Mysuru");
    await user.click(within(dialog).getByRole("button", { name: "Save profile" }));

    expect(mockedPatch).toHaveBeenCalledTimes(1);
    expect(mockedPatch.mock.calls[0]![0]).toBe("/platform/organizations/" + ORG_ID);
    // Only the difference: the untouched fields are omitted, so the server leaves them alone.
    expect(mockedPatch.mock.calls[0]![1]).toEqual({ name: "Renamed Org", city: "Mysuru" });

    // The dialog closes on success so the refreshed profile underneath is what the operator reads.
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });

  it("sends an empty string when a field is cleared, because that is how a value is removed", async () => {
    mockOrganization(seeded);
    mockedPatch.mockResolvedValue({ data: organization({ ...seeded, contactPhone: null }), message: "ok" });
    renderScreen();

    const { user, dialog } = await openEditDialog();
    await user.clear(within(dialog).getByLabelText("Contact phone"));
    await user.click(within(dialog).getByRole("button", { name: "Save profile" }));

    // An omitted field means "leave it alone"; only "" clears the stored value.
    expect(mockedPatch.mock.calls[0]![1]).toEqual({ contactPhone: "" });
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });

  it("keeps the dialog open and shows the server's message when the save is refused", async () => {
    mockOrganization(seeded);
    mockedPatch.mockRejectedValue(new Error("refused"));
    renderScreen();

    const { user, dialog } = await openEditDialog();
    const name = within(dialog).getByLabelText("Name (required)");
    await user.clear(name);
    await user.type(name, "Renamed Org");
    await user.click(within(dialog).getByRole("button", { name: "Save profile" }));

    const alert = await within(dialog).findByRole("alert");
    expect(alert).toHaveTextContent("The organization profile could not be saved.");
    // Still open, with what the operator typed intact — closing would throw their edit away.
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(within(dialog).getByLabelText("Name (required)")).toHaveValue("Renamed Org");
  });

  it("blocks a malformed contact email before it reaches the server", async () => {
    mockOrganization(seeded);
    renderScreen();

    const { user, dialog } = await openEditDialog();
    const email = within(dialog).getByLabelText("Contact email");
    await user.clear(email);
    await user.type(email, "not-an-email");
    await user.click(within(dialog).getByRole("button", { name: "Save profile" }));

    expect(await within(dialog).findByText("Enter a valid email address")).toBeInTheDocument();
    expect(mockedPatch).not.toHaveBeenCalled();
  });

  it("closes on Cancel without saving anything", async () => {
    mockOrganization(seeded);
    renderScreen();

    const { user, dialog } = await openEditDialog();
    const name = within(dialog).getByLabelText("Name (required)");
    await user.clear(name);
    await user.type(name, "Discarded edit");
    await user.click(within(dialog).getByRole("button", { name: "Cancel" }));

    // Abandoning an edit is not a write: nothing is sent, and the dialog is gone.
    expect(mockedPatch).not.toHaveBeenCalled();
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    // The profile underneath still shows the stored name, not the abandoned draft.
    expect(screen.getByRole("heading", { name: "Default Organization" })).toBeInTheDocument();
  });

  it("shows 'Not set' after an edit clears a value", async () => {
    // Two reads: the screen fetches once, then refetches after the successful PATCH invalidates the
    // detail. The second answer is the organization with the phone number cleared.
    const cleared = { ...seeded, contactPhone: null };
    mockedGet.mockResolvedValueOnce({ data: organization(seeded), message: "ok" });
    mockedGet.mockResolvedValueOnce({ data: organization(cleared), message: "ok" });
    mockedPatch.mockResolvedValue({ data: organization(cleared), message: "ok" });
    renderScreen();

    const { user, dialog } = await openEditDialog();
    await user.clear(within(dialog).getByLabelText("Contact phone"));
    await user.click(within(dialog).getByRole("button", { name: "Save profile" }));

    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    // Clearing is a real state, not a blank: the screen states "Not set" rather than rendering an
    // empty value the operator cannot tell from a failed load.
    const term = await screen.findByText("Contact phone");
    expect(term.nextSibling).toHaveTextContent("Not set");
  });

  it("still renders 'Not set' for a profile field the organization has not filled in", async () => {
    // The display contract does not depend on edit history: an absent field is stated, never hidden.
    mockOrganization({ city: null });
    renderScreen();

    await screen.findByRole("heading", { name: "Default Organization" });
    expect(screen.getByText("City").nextSibling).toHaveTextContent("Not set");
  });
});