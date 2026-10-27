import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("next/navigation", () => ({
  usePathname: () => "/platform/organizations/11111111-1111-1111-1111-111111111111/admins",
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock("@/lib/api-client", () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiPut: vi.fn(),
  apiPatch: vi.fn(),
  apiDelete: vi.fn(),
  setUnauthorizedHandler: vi.fn(),
}));

import { apiDelete, apiGet, apiPatch, apiPost } from "@/lib/api-client";
import { CreateAdminForm } from "@/features/platform/components/create-admin-form";
import { OrganizationAdminsScreen } from "@/features/platform/components/organization-admins-screen";

const mockedGet = vi.mocked(apiGet);
const mockedPatch = vi.mocked(apiPatch);
const mockedPost = vi.mocked(apiPost);
const mockedDelete = vi.mocked(apiDelete);

const ORG_ID = "11111111-1111-1111-1111-111111111111";
const PRIMARY_ID = "22222222-2222-2222-2222-222222222222";
const SECOND_ID = "33333333-3333-3333-3333-333333333333";
const ARCHIVED_ID = "44444444-4444-4444-4444-444444444444";

/** The active, assignable roles the screen may offer. ADMIN/SUPER_ADMIN are deliberately absent. */
const ROLES = [
  { id: "role-staff", code: "STAFF", name: "Staff", description: null, status: "ACTIVE" },
  { id: "role-student", code: "STUDENT", name: "Student", description: null, status: "ACTIVE" },
  { id: "role-exam", code: "EXAM_MANAGER", name: "Exam manager", description: null, status: "ACTIVE" },
  { id: "role-retired", code: "RETIRED", name: "Retired", description: null, status: "ARCHIVED" },
  { id: "role-admin", code: "ADMIN", name: "Administrator", description: null, status: "ACTIVE" },
  { id: "role-super", code: "SUPER_ADMIN", name: "Super admin", description: null, status: "ACTIVE" },
];

/**
 * Build an ApiResult. Typed here rather than inline because the mocked generic apiGet requires the
 * full result shape, including the unwrapped envelope message.
 */
function result<T>(data: T, pagination?: { page: number; size: number; totalElements: number; totalPages: number }) {
  return Promise.resolve({ data, message: "ok", ...(pagination ? { pagination } : {}) });
}

function admin(overrides: Record<string, unknown> = {}) {
  return {
    userId: SECOND_ID,
    firstName: "Second",
    lastName: "Admin",
    email: "second@example.test",
    status: "ACTIVE",
    isPrimary: false,
    createdAt: "2026-01-01T00:00:00Z",
    roles: ["STUDENT"],
    ...overrides,
  };
}

function renderScreen() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <OrganizationAdminsScreen organizationId={ORG_ID} />
    </QueryClientProvider>,
  );
}

function renderCreateForm() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <CreateAdminForm organizationId={ORG_ID} />
    </QueryClientProvider>,
  );
}

function organization(overrides: Record<string, unknown> = {}) {
  return {
    id: ORG_ID,
    code: "ACME",
    name: "Acme",
    status: "ACTIVE",
    description: null,
    adminCount: 1,
    userCount: 1,
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
    primaryAdminUserId: PRIMARY_ID,
    primaryAdminEmail: "primary@example.test",
    ...overrides,
  };
}

function mockEndpoints(
  options: {
    admins?: unknown[];
    primaryAdminEmail?: string | null;
    primaryAdminUserId?: string | null;
  } = {},
) {
  mockedGet.mockImplementation((url: string) => {
    if (url.endsWith("/admins")) {
      const admins = options.admins ?? [admin()];
      return result(admins, {
        page: 0,
        size: 10,
        totalElements: admins.length,
        totalPages: 1,
      });
    }
    if (url.endsWith("/roles")) {
      return result(ROLES, { page: 0, size: 100, totalElements: ROLES.length, totalPages: 1 });
    }
    return result(
      organization({
        primaryAdminUserId:
          options.primaryAdminUserId === undefined ? PRIMARY_ID : options.primaryAdminUserId,
        primaryAdminEmail:
          options.primaryAdminEmail === undefined
            ? "primary@example.test"
            : options.primaryAdminEmail,
      }),
    );
  });
}

/** The URL the admin list was requested with, including its params. */
function adminListCalls() {
  return mockedGet.mock.calls.filter((call) => String(call[0]).endsWith("/admins"));
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("organization admins screen", () => {
  it("lists administrators and marks the primary one", async () => {
    mockEndpoints({
      admins: [
        admin({ userId: PRIMARY_ID, email: "primary@example.test", isPrimary: true, roles: [] }),
        admin(),
      ],
    });
    renderScreen();

    expect(await screen.findByText("primary@example.test")).toBeInTheDocument();
    expect(screen.getByText("second@example.test")).toBeInTheDocument();
    // "Primary" appears twice by design: the column header and the row badge. Assert the badge,
    // i.e. the cell content, so the test pins the marking rather than the table chrome.
    expect(screen.getByRole("columnheader", { name: "Primary" })).toBeInTheDocument();
    expect(screen.getAllByText("Primary")).toHaveLength(2);
    expect(screen.getByText(/Primary administrator: primary@example\.test/)).toBeInTheDocument();
  });

  it("says role editing is blocked when no primary is designated", async () => {
    mockEndpoints({ primaryAdminEmail: null, primaryAdminUserId: null });
    renderScreen();

    await waitFor(() =>
      expect(screen.getByText(/No primary administrator is designated/)).toBeInTheDocument(),
    );
  });

  it("corrects the non-administrator roles of an administrator", async () => {
    const user = userEvent.setup();
    mockedPatch.mockResolvedValue({ data: admin({ roles: ["STAFF"] }), message: "ok" });
    mockEndpoints();
    renderScreen();

    await user.click(await screen.findByRole("button", { name: "Edit roles" }));
    // The editor is a dialog outside the table now, not an inline panel.
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByRole("heading", { name: /Roles for second@example\.test/ })).toBeInTheDocument();

    // The editor seeds its checked set from the row, so STUDENT starts ticked and STAFF does not.
    const student = await within(dialog).findByLabelText("Student");
    const staff = within(dialog).getByLabelText("Staff");
    expect(student).toBeChecked();
    expect(staff).not.toBeChecked();

    await user.click(staff);
    expect(staff).toBeChecked();
    expect(student).toBeChecked();

    await user.click(within(dialog).getByRole("button", { name: "Save roles" }));

    await waitFor(() => expect(mockedPatch).toHaveBeenCalledTimes(1));
    expect(mockedPatch).toHaveBeenCalledWith(
      "/platform/organizations/" + ORG_ID + "/admins/" + SECOND_ID + "/roles",
      { roles: ["STUDENT", "STAFF"] },
    );
  });

  it("refuses to offer archiving for the primary administrator", async () => {
    mockEndpoints({
      admins: [admin({ userId: PRIMARY_ID, email: "primary@example.test", isPrimary: true })],
    });
    renderScreen();

    await screen.findByText("primary@example.test");
    expect(screen.getByText("Cannot archive the primary administrator")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Archive" })).not.toBeInTheDocument();
  });

  it("archives a non-primary administrator after confirmation", async () => {
    const user = userEvent.setup();
    mockedDelete.mockResolvedValue(undefined);
    mockEndpoints();
    renderScreen();

    await user.click(await screen.findByRole("button", { name: "Archive" }));
    // The confirmation is a real dialog now, not window.confirm.
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Archive" }));

    await waitFor(() => expect(mockedDelete).toHaveBeenCalled());
    expect(mockedDelete).toHaveBeenCalledWith(
      "/platform/organizations/" + ORG_ID + "/admins/" + SECOND_ID,
    );
  });

  it("does not archive when the confirmation is cancelled", async () => {
    const user = userEvent.setup();
    mockEndpoints();
    renderScreen();

    await user.click(await screen.findByRole("button", { name: "Archive" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Cancel" }));
    expect(mockedDelete).not.toHaveBeenCalled();
  });

  it("shows the empty state when the organization has no administrators", async () => {
    mockEndpoints({ admins: [] });
    renderScreen();

    expect(await screen.findByRole("note")).toHaveTextContent("No administrators match this search.");
  });

  // ---- New behaviour (ADR-0054) ----

  it("shows an archived row as Archived with a Restore action and no editing actions", async () => {
    mockEndpoints({
      admins: [
        admin({
          userId: ARCHIVED_ID,
          email: "archived@example.test",
          status: "ARCHIVED",
          roles: ["STAFF"],
        }),
      ],
    });
    renderScreen();

    await screen.findByText("archived@example.test");
    // The badge already maps ARCHIVED to a muted pill; the word is also present as the label.
    expect(screen.getAllByText("Archived").length).toBeGreaterThan(0);
    expect(screen.getByRole("button", { name: "Restore" })).toBeInTheDocument();

    // Both editing actions exist but are disabled, so the reason is discoverable rather than the
    // control vanishing without explanation.
    expect(screen.getByRole("button", { name: "Edit roles" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Make primary" })).toBeDisabled();
    expect(screen.queryByRole("button", { name: "Archive" })).not.toBeInTheDocument();
  });

  it("restores an archived administrator", async () => {
    const user = userEvent.setup();
    mockedPost.mockResolvedValue({
      data: admin({ userId: ARCHIVED_ID, status: "ACTIVE" }),
      message: "ok",
    });
    mockEndpoints({
      admins: [admin({ userId: ARCHIVED_ID, email: "archived@example.test", status: "ARCHIVED" })],
    });
    renderScreen();

    await user.click(await screen.findByRole("button", { name: "Restore" }));
    await waitFor(() =>
      expect(mockedPost).toHaveBeenCalledWith(
        "/platform/organizations/" + ORG_ID + "/admins/" + ARCHIVED_ID + "/restore",
        {},
      ),
    );
  });

  it("requests status=ARCHIVED when the Archived filter is chosen", async () => {
    const user = userEvent.setup();
    mockEndpoints();
    renderScreen();

    await screen.findByText("second@example.test");
    // The default ("All") must send no status at all: the server's default is "not archived", which
    // is broader than status=ACTIVE.
    expect(adminListCalls()[0]![1]).not.toHaveProperty("status");

    await user.click(screen.getByRole("button", { name: "Archived" }));

    await waitFor(() => {
      const last = adminListCalls().at(-1);
      expect(last![1]).toMatchObject({ status: "ARCHIVED", page: 0 });
    });
  });

  it("sends status=ACTIVE when the Active filter is chosen", async () => {
    const user = userEvent.setup();
    mockEndpoints();
    renderScreen();

    await screen.findByText("second@example.test");
    await user.click(screen.getByRole("button", { name: "Active" }));

    await waitFor(() => {
      const last = adminListCalls().at(-1);
      expect(last![1]).toMatchObject({ status: "ACTIVE", page: 0 });
    });
  });

  it("transfers the primary designation to a non-primary administrator", async () => {
    const user = userEvent.setup();
    mockedPatch.mockResolvedValue({ data: organization(), message: "ok" });
    mockEndpoints();
    renderScreen();

    await user.click(await screen.findByRole("button", { name: "Make primary" }));

    await waitFor(() => expect(mockedPatch).toHaveBeenCalledTimes(1));
    expect(mockedPatch).toHaveBeenCalledWith(
      "/platform/organizations/" + ORG_ID + "/primary-admin",
      { userId: SECOND_ID },
    );
  });

  it("offers a checkbox per assignable role, excluding ADMIN and SUPER_ADMIN", async () => {
    const user = userEvent.setup();
    mockEndpoints();
    renderScreen();

    await user.click(await screen.findByRole("button", { name: "Edit roles" }));
    const dialog = await screen.findByRole("dialog");

    // Only ACTIVE, non-administrator roles are offered: the server rejects the others.
    expect(await within(dialog).findByLabelText("Staff")).toBeInTheDocument();
    expect(within(dialog).getByLabelText("Student")).toBeInTheDocument();
    expect(within(dialog).getByLabelText("Exam manager")).toBeInTheDocument();
    expect(within(dialog).queryByLabelText("Administrator")).not.toBeInTheDocument();
    expect(within(dialog).queryByLabelText("Super admin")).not.toBeInTheDocument();
    // An archived role is not assignable either.
    expect(within(dialog).queryByLabelText("Retired")).not.toBeInTheDocument();
  });

  it("submits exactly the codes that were ticked", async () => {
    const user = userEvent.setup();
    mockedPatch.mockResolvedValue({ data: admin({ roles: ["STAFF", "EXAM_MANAGER"] }), message: "ok" });
    mockEndpoints();
    renderScreen();

    await user.click(await screen.findByRole("button", { name: "Edit roles" }));
    const dialog = await screen.findByRole("dialog");

    // Untick the seeded STUDENT so the assertion proves deselection works too.
    await user.click(await within(dialog).findByLabelText("Student"));
    await user.click(within(dialog).getByLabelText("Staff"));
    await user.click(within(dialog).getByLabelText("Exam manager"));
    await user.click(within(dialog).getByRole("button", { name: "Save roles" }));

    await waitFor(() => expect(mockedPatch).toHaveBeenCalledTimes(1));
    expect(mockedPatch).toHaveBeenCalledWith(
      "/platform/organizations/" + ORG_ID + "/admins/" + SECOND_ID + "/roles",
      { roles: ["STAFF", "EXAM_MANAGER"] },
    );
  });

  it("surfaces a server refusal instead of silently failing", async () => {
    const user = userEvent.setup();
    mockedPatch.mockRejectedValue(new Error("nope"));
    mockEndpoints();
    renderScreen();

    await user.click(await screen.findByRole("button", { name: "Make primary" }));

    expect(
      await screen.findByText(/The administration request could not be completed/),
    ).toBeInTheDocument();
  });

  it("edits roles in a dialog, outside the table's grid roles", async () => {
    const user = userEvent.setup();
    mockEndpoints();
    renderScreen();

    const editButton = await screen.findByRole("button", { name: "Edit roles" });
    // The "Other roles" cell value is what the row shows; the editor must not appear inside a cell.
    const table = screen.getByRole("table");
    await user.click(editButton);

    const dialog = await screen.findByRole("dialog");
    expect(table.contains(dialog)).toBe(false);
    // The dialog is a real modal carrying the fields, so focus lands in it and Escape closes it.
    expect(dialog).toHaveAttribute("aria-modal", "true");
    expect(await within(dialog).findByRole("checkbox", { name: "Staff" })).not.toBeChecked();

    await user.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    // Cancelling changes nothing.
    expect(mockedPatch).not.toHaveBeenCalled();
  });

  it("renders no inline roles panel above the grid", async () => {
    const user = userEvent.setup();
    mockEndpoints();
    renderScreen();

    await screen.findByText("second@example.test");
    // Before the editor is opened there is no form and no checkbox floating on the page: the screen
    // is the filter row plus the grid, nothing else.
    expect(screen.queryByRole("form")).not.toBeInTheDocument();
    expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Edit roles" }));
    // Once open, the checkboxes exist only inside the dialog.
    const dialog = await screen.findByRole("dialog");
    expect(screen.queryAllByRole("checkbox").every((box) => dialog.contains(box))).toBe(true);
  });
});

describe("create administrator form", () => {
  it("labels the primary checkbox accessibly and explains the consequence", () => {
    renderCreateForm();

    const checkbox = screen.getByLabelText(
      "Make this administrator the primary administrator",
    );
    expect(checkbox).not.toBeChecked();
    expect(checkbox).toHaveAttribute("aria-describedby", "makePrimary-help");
    expect(
      screen.getByText(
        "The primary administrator is the only one who can change other users' roles.",
      ),
    ).toBeInTheDocument();
  });

  it("submits makePrimary: true when the checkbox is checked", async () => {
    const user = userEvent.setup();
    mockedPost.mockResolvedValue({
      message: "ok",
      data: {
        userId: "u-1",
        email: "ada@example.com",
        invitationSentAt: "2026-01-01T00:00:00Z",
        isPrimary: true,
      },
    });
    renderCreateForm();

    await user.type(screen.getByLabelText("First name"), "Ada");
    await user.type(screen.getByLabelText("Last name"), "Lovelace");
    await user.type(screen.getByLabelText("Email"), "ada@example.com");
    await user.click(
      screen.getByLabelText("Make this administrator the primary administrator"),
    );
    await user.click(screen.getByRole("button", { name: "Create administrator" }));

    expect(mockedPost).toHaveBeenCalledWith(
      "/platform/organizations/" + ORG_ID + "/admins",
      expect.objectContaining({
        firstName: "Ada",
        lastName: "Lovelace",
        email: "ada@example.com",
        makePrimary: true,
      }),
    );
    // The confirmation reports the designation the server returned, not what was requested.
    expect(await screen.findByText(/They are now the primary administrator/)).toBeInTheDocument();
  });

  it("submits makePrimary: false when the checkbox is left unticked", async () => {
    const user = userEvent.setup();
    mockedPost.mockResolvedValue({
      message: "ok",
      data: {
        userId: "u-2",
        email: "grace@example.com",
        invitationSentAt: "2026-01-01T00:00:00Z",
        isPrimary: false,
      },
    });
    renderCreateForm();

    await user.type(screen.getByLabelText("First name"), "Grace");
    await user.type(screen.getByLabelText("Last name"), "Hopper");
    await user.type(screen.getByLabelText("Email"), "grace@example.com");
    await user.click(screen.getByRole("button", { name: "Create administrator" }));

    expect(mockedPost).toHaveBeenCalledWith(
      "/platform/organizations/" + ORG_ID + "/admins",
      expect.objectContaining({ makePrimary: false }),
    );
    expect(
      await screen.findByText(/They are not the primary administrator/),
    ).toBeInTheDocument();
  });

  it("reports the field errors the contract enforces before calling the server", async () => {
    const user = userEvent.setup();
    renderCreateForm();

    await user.click(screen.getByRole("button", { name: "Create administrator" }));

    expect(await screen.findByText("First name is required")).toBeInTheDocument();
    expect(screen.getByText("Last name is required")).toBeInTheDocument();
    expect(mockedPost).not.toHaveBeenCalled();
  });
});
