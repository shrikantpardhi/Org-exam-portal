import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/api-client", () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiPut: vi.fn(),
  apiDelete: vi.fn(),
  setUnauthorizedHandler: vi.fn(),
}));

const push = vi.hoisted(() => vi.fn());
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));

import { apiGet, apiPost } from "@/lib/api-client";
import { CreateUserForm } from "@/features/administration/components/create-user-form";

const mockedGet = vi.mocked(apiGet);
const mockedPost = vi.mocked(apiPost);

const roles = [
  { id: "role-admin", code: "ADMIN", name: "Administrator", description: null, status: "ACTIVE" },
  { id: "role-super", code: "SUPER_ADMIN", name: "Super admin", description: null, status: "ACTIVE" },
  { id: "role-staff", code: "STAFF", name: "Staff", description: null, status: "ACTIVE" },
  { id: "role-student", code: "STUDENT", name: "Student", description: null, status: "ACTIVE" },
];

function renderForm() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <CreateUserForm />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockedGet.mockResolvedValue({
    message: "ok",
    data: roles,
    pagination: { page: 0, size: 100, totalElements: 4, totalPages: 1 },
  } as never);
});

describe("create user form", () => {
  it("offers only the creatable roles, excluding ADMIN and SUPER_ADMIN", async () => {
    renderForm();

    expect(await screen.findByLabelText("Staff")).toBeInTheDocument();
    expect(screen.getByLabelText("Student")).toBeInTheDocument();
    expect(screen.queryByLabelText("Administrator")).toBeNull();
    expect(screen.queryByLabelText("Super admin")).toBeNull();
  });

  it("posts the frozen create body and routes to the new user", async () => {
    mockedPost.mockResolvedValue({
      message: "ok",
      data: {
        id: "u1",
        organizationId: "org-1",
        firstName: "Ada",
        lastName: "Lovelace",
        email: "ada@example.com",
        mobile: null,
        status: "ACTIVE",
        roles: ["STAFF"],
        createdOn: null,
        updatedOn: null,
      },
    } as never);

    const user = userEvent.setup();
    renderForm();

    await user.type(await screen.findByLabelText("First name"), "Ada");
    await user.type(screen.getByLabelText("Last name"), "Lovelace");
    await user.type(screen.getByLabelText("Email"), "ada@example.com");
    await user.click(await screen.findByLabelText("Staff"));

    await user.click(screen.getByRole("button", { name: "Create and send invite" }));

    expect(mockedPost).toHaveBeenCalledWith(
      "/users",
      expect.objectContaining({
        firstName: "Ada",
        lastName: "Lovelace",
        email: "ada@example.com",
        roles: ["role-staff"],
      }),
    );
    expect(push).toHaveBeenCalledWith("/admin/users/u1");
  });
});
