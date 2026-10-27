import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
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

const push = vi.fn();

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => "/platform/organizations/new",
  useRouter: () => ({ push, replace: vi.fn(), refresh: vi.fn() }),
}));

import { apiPost } from "@/lib/api-client";
import { OrganizationForm } from "@/features/platform/components/organization-form";

const mockedPost = vi.mocked(apiPost);

function renderForm() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <OrganizationForm />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

/**
 * The create-organization form (ADR-0056).
 *
 * <p>The profile is collected up front so an operator registering an institute does not have to
 * create a bare row and re-open it to fill in its address. Only the code and the name are required,
 * so the tests below care most about the two ways that could go wrong: the optional fields becoming
 * mandatory, and the ten fields being hidden from the operator.
 */
describe("create organization form", () => {
  it("renders the three profile groups with the required fields marked", () => {
    renderForm();

    // Grouped, not one flat column: the operator has to be able to tell 2 required fields from 13.
    expect(screen.getByRole("group", { name: "Identity" })).toBeInTheDocument();
    expect(screen.getByRole("group", { name: "Address" })).toBeInTheDocument();
    expect(screen.getByRole("group", { name: "Contact" })).toBeInTheDocument();

    expect(screen.getByLabelText("Code (required)")).toBeInTheDocument();
    expect(screen.getByLabelText("Name (required)")).toBeInTheDocument();

    // Every profile column from V37 has an input, otherwise it still cannot be set at creation.
    for (const label of [
      "Address line 1",
      "Address line 2",
      "City",
      "State or region",
      "Postal code",
      "Country",
      "Contact email",
      "Contact phone",
      "Website",
      "Logo URL",
    ]) {
      expect(screen.getByLabelText(label)).toBeInTheDocument();
    }

    // The optional sections say so once, at section level, rather than on ten labels.
    const address = screen.getByRole("group", { name: "Address" });
    expect(address).toHaveTextContent(/Optional/);
  });

  it("creates an organization with only the required fields", async () => {
    const user = userEvent.setup();
    mockedPost.mockResolvedValue({
      data: { id: "org-1", code: "acme", name: "Acme University", status: "ACTIVE" },
      message: "ok",
    });
    renderForm();

    await user.type(screen.getByLabelText("Code (required)"), "acme");
    await user.type(screen.getByLabelText("Name (required)"), "Acme University");
    await user.click(screen.getByRole("button", { name: "Create organization" }));

    expect(mockedPost).toHaveBeenCalledTimes(1);
    expect(mockedPost.mock.calls[0]![0]).toBe("/platform/organizations");
    // The blank profile fields are omitted rather than sent as "": "not provided" is the absence of
    // a value, and the request should say exactly what the operator supplied.
    expect(mockedPost.mock.calls[0]![1]).toEqual({
      code: "acme",
      name: "Acme University",
    });
    // The created organization is handed over: the detail page states the remaining step.
    expect(push).toHaveBeenCalledWith("/platform/organizations/org-1?created=1");
  });

  it("sends the profile fields the operator did supply", async () => {
    const user = userEvent.setup();
    mockedPost.mockResolvedValue({
      data: { id: "org-1", code: "acme", name: "Acme University", status: "ACTIVE" },
      message: "ok",
    });
    renderForm();

    await user.type(screen.getByLabelText("Code (required)"), "acme");
    await user.type(screen.getByLabelText("Name (required)"), "Acme University");
    await user.type(screen.getByLabelText("City"), "Bengaluru");
    await user.type(screen.getByLabelText("Country"), "India");
    await user.type(screen.getByLabelText("Contact email"), "office@example.edu");
    await user.click(screen.getByRole("button", { name: "Create organization" }));

    expect(mockedPost.mock.calls[0]![1]).toEqual({
      code: "acme",
      name: "Acme University",
      city: "Bengaluru",
      country: "India",
      contactEmail: "office@example.edu",
    });
  });

  it("blocks submit when the contact email is not a valid address", async () => {
    const user = userEvent.setup();
    renderForm();

    await user.type(screen.getByLabelText("Code (required)"), "acme");
    await user.type(screen.getByLabelText("Name (required)"), "Acme University");
    await user.type(screen.getByLabelText("Contact email"), "not-an-email");
    await user.click(screen.getByRole("button", { name: "Create organization" }));

    expect(await screen.findByText("Enter a valid email address")).toBeInTheDocument();
    // Nothing reached the server: the client refuses the body the server would reject with a 400.
    expect(mockedPost).not.toHaveBeenCalled();
    expect(push).not.toHaveBeenCalled();
  });

  it("blocks submit and names the missing required fields", async () => {
    const user = userEvent.setup();
    renderForm();

    await user.click(screen.getByRole("button", { name: "Create organization" }));

    expect(await screen.findByText("A code is required")).toBeInTheDocument();
    expect(screen.getByText("A name is required")).toBeInTheDocument();
    expect(mockedPost).not.toHaveBeenCalled();
  });

  it("offers a Cancel that returns to the organizations list without submitting", async () => {
    renderForm();

    // A link rather than a button: it cannot submit the form it sits in, and it goes to the list
    // the form was opened from, so a deep link straight to /new still has a way back.
    const cancel = screen.getByRole("link", { name: "Cancel" });
    expect(cancel).toHaveAttribute("href", "/platform/organizations");
    expect(mockedPost).not.toHaveBeenCalled();
  });

  it("still offers Cancel while a create is in flight", async () => {
    const user = userEvent.setup();
    // A promise that never settles keeps the mutation pending for the assertion.
    mockedPost.mockReturnValue(new Promise(() => undefined));
    renderForm();

    await user.type(screen.getByLabelText("Code (required)"), "acme");
    await user.type(screen.getByLabelText("Name (required)"), "Acme University");
    await user.click(screen.getByRole("button", { name: "Create organization" }));

    await screen.findByRole("button", { name: "Creating…" });
    // The way out is not taken away exactly when the operator wants it.
    expect(screen.getByRole("link", { name: "Cancel" })).toBeInTheDocument();
  });

  it("reports a server refusal without navigating away", async () => {
    const user = userEvent.setup();
    mockedPost.mockRejectedValue(new Error("duplicate code"));
    renderForm();

    await user.type(screen.getByLabelText("Code (required)"), "acme");
    await user.type(screen.getByLabelText("Name (required)"), "Acme University");
    await user.click(screen.getByRole("button", { name: "Create organization" }));

    expect(
      await screen.findByText("The organization could not be created."),
    ).toBeInTheDocument();
    expect(push).not.toHaveBeenCalled();
  });
});