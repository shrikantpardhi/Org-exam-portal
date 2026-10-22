import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactElement } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/api-client", () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiPut: vi.fn(),
  apiDelete: vi.fn(),
  setUnauthorizedHandler: vi.fn(),
}));

vi.mock("@/lib/auth/auth-context", () => ({
  useAuth: () => ({ can: () => true, status: "authenticated", permissions: [] }),
}));

import { apiGet, apiPut } from "@/lib/api-client";
import { TemplateEditScreen } from "@/features/communication/components/template-edit-screen";
import { TemplateListScreen } from "@/features/communication/components/template-list-screen";
import { TemplatePreviewScreen } from "@/features/communication/components/template-preview-screen";

const mockedGet = vi.mocked(apiGet);
const mockedPut = vi.mocked(apiPut);

const template = {
  id: "t1",
  code: "WELCOME",
  name: "Welcome email",
  subject: "Hi {{studentName}}",
  body: "<p>Hello {{studentName}}</p><script>alert(1)</script>",
  status: "ACTIVE",
};

function renderWithClient(ui: ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

beforeEach(() => {
  vi.clearAllMocks();
  mockedGet.mockImplementation((url: string) => {
    if (url === "/email-templates") {
      return Promise.resolve({ message: "Templates retrieved", data: [template] } as never);
    }
    if (url === "/email-templates/t1") {
      return Promise.resolve({ message: "Template retrieved", data: template } as never);
    }
    return Promise.reject(new Error(`Unexpected GET ${url}`));
  });
  mockedPut.mockResolvedValue({
    message: "Template updated",
    data: { ...template, name: "Updated welcome email" },
  } as never);
});

describe("template administration", () => {
  it("lists templates with edit and preview links", async () => {
    renderWithClient(<TemplateListScreen />);

    expect(await screen.findByText("WELCOME")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Edit" })).toHaveAttribute(
      "href",
      "/admin/communication/templates/t1",
    );
    expect(screen.getByRole("link", { name: "Preview" })).toHaveAttribute(
      "href",
      "/admin/communication/templates/t1/preview",
    );
  });

  it("edits name, subject and body while keeping the code immutable", async () => {
    const user = userEvent.setup();
    renderWithClient(<TemplateEditScreen templateId="t1" />);

    expect(await screen.findByDisplayValue("Welcome email")).toBeInTheDocument();
    expect(screen.getByText("WELCOME")).toBeInTheDocument();
    expect(screen.queryByLabelText(/^Code/)).toBeNull();

    const name = screen.getByLabelText("Name");
    await user.clear(name);
    await user.type(name, "Updated welcome email");
    await user.click(screen.getByRole("button", { name: "Save template" }));

    await waitFor(() => {
      expect(mockedPut).toHaveBeenCalledWith(
        "/email-templates/t1",
        expect.objectContaining({ name: "Updated welcome email" }),
      );
    });
  });

  it("previews with synthetic data without executing template HTML", async () => {
    const { container } = renderWithClient(<TemplatePreviewScreen templateId="t1" />);

    expect(await screen.findByText("Hi Sample Student")).toBeInTheDocument();
    expect(screen.getByText(/Hello Sample Student/)).toBeInTheDocument();
    expect(container.querySelector("script")).toBeNull();
    expect(screen.getByRole("region", { name: "Template preview" })).toBeInTheDocument();
  });
});
