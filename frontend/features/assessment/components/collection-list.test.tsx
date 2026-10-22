import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { CollectionDetail } from "@/features/assessment/components/collection-list";
import { apiDelete, apiGet, apiPost } from "@/lib/api-client";

vi.mock("@/lib/api-client", () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiPut: vi.fn(),
  apiDelete: vi.fn(),
  setUnauthorizedHandler: vi.fn(),
}));

const apiGetMock = vi.mocked(apiGet);
const apiPostMock = vi.mocked(apiPost);
const apiDeleteMock = vi.mocked(apiDelete);

/**
 * Collection membership ({@code specs/13-P4-Assessment-V1.5.md §110}).
 *
 * <p>Membership changes go through the frozen CollectionAssessment routes; the UI never edits a local
 * list and calls that a membership change.
 */
function renderDetail() {
  render(
    <QueryClientProvider
      client={new QueryClient({
        defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
      })}
    >
      <CollectionDetail collectionId="col-1" />
    </QueryClientProvider>,
  );
}

describe("CollectionDetail", () => {
  beforeEach(() => {
    apiGetMock.mockReset();
    apiPostMock.mockReset();
    apiDeleteMock.mockReset();

    apiGetMock.mockImplementation((url: string) => {
      if (url === "/assessment-collections/col-1") {
        return Promise.resolve({
          data: {
            id: "col-1",
            code: "SSC-SERIES",
            name: "SSC Series",
            description: "A practice series",
            status: "ACTIVE",
            assessmentIds: ["a-1"],
          },
          message: "ok",
        });
      }
      if (url.startsWith("/assessments")) {
        return Promise.resolve({
          data: [
            { id: "a-1", code: "MOCK-1", name: "Mock 1", status: "ACTIVE" },
            { id: "a-2", code: "MOCK-2", name: "Mock 2", status: "ACTIVE" },
          ],
          message: "ok",
        });
      }
      return Promise.resolve({ data: [], message: "ok" });
    });
    apiPostMock.mockResolvedValue({ data: {}, message: "ok" });
    apiDeleteMock.mockResolvedValue(undefined as unknown as void);
  });

  it("lists current members and excludes them from the add picker", async () => {
    renderDetail();

    await screen.findByRole("heading", { name: "SSC Series" });

    // The member list shows the current member by name.
    expect(await screen.findByText("Mock 1")).toBeInTheDocument();

    // The add picker must not offer an assessment already in the collection.
    await screen.findByRole("option", { name: "Mock 2" });
    const picker = screen.getByLabelText("Add assessment") as HTMLSelectElement;
    const options = Array.from(picker.options).map((option) => option.value);
    expect(options).toContain("a-2");
    expect(options).not.toContain("a-1");
  });

  it("adds an assessment through the frozen membership path", async () => {
    renderDetail();

    await screen.findByRole("heading", { name: "SSC Series" });
    await screen.findByRole("option", { name: "Mock 2" });

    await userEvent.selectOptions(screen.getByLabelText("Add assessment"), "a-2");
    await userEvent.click(screen.getByRole("button", { name: "Add" }));

    await waitFor(() => expect(apiPostMock).toHaveBeenCalledTimes(1));
    expect(apiPostMock.mock.calls[0]![0]).toBe("/assessment-collections/col-1/assessments");
    expect(apiPostMock.mock.calls[0]![1]).toEqual({ assessmentId: "a-2" });
  });

  it("removes a member through the frozen membership path", async () => {
    renderDetail();

    await screen.findByRole("heading", { name: "SSC Series" });
    await userEvent.click(await screen.findByRole("button", { name: "Remove Mock 1" }));

    await waitFor(() => expect(apiDeleteMock).toHaveBeenCalledTimes(1));
    expect(apiDeleteMock.mock.calls[0]![0]).toBe("/assessment-collections/col-1/assessments/a-1");
  });

  it("refuses to add without a chosen assessment", async () => {
    renderDetail();

    await screen.findByRole("heading", { name: "SSC Series" });
    await userEvent.click(screen.getByRole("button", { name: "Add" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Choose an assessment to add.");
    expect(apiPostMock).not.toHaveBeenCalled();
  });
});
