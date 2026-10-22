import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { BlueprintBuilder } from "@/features/assessment/components/blueprint-builder";
import { apiGet, apiPost } from "@/lib/api-client";

vi.mock("@/lib/api-client", () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiPut: vi.fn(),
  apiDelete: vi.fn(),
  setUnauthorizedHandler: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useParams: () => ({}),
}));

const apiGetMock = vi.mocked(apiGet);
const apiPostMock = vi.mocked(apiPost);

/**
 * Blueprint builder loading ({@code specs/13-P4-Assessment-V1.5.md §22, §99}).
 *
 * <p>A stored configuration must map back into the form exactly, including section names that look
 * like exam names, and a published version must be presented as immutable.
 */
const storedConfiguration = {
  sections: [
    { name: "IBPS", displayOrder: 1, questionCount: 25, marks: 50, durationMinutes: 20 },
    { name: "Quantitative Aptitude", displayOrder: 2, questionCount: 35, marks: 70, durationMinutes: 40 },
  ],
  navigation: { allowSectionSwitch: false, mode: "SECTION_SEQUENTIAL" },
  timer: { mode: "SECTION", serverAuthoritative: true, autoSubmitOnExpiry: false },
  submission: { allowManualSubmit: true, autoSubmitOnExpiry: false, requireConfirmation: true },
  validation: { responseRequiredBeforeNext: true, sectionCompletionRequired: false },
  presentation: { palettePosition: "LEFT", showProgress: false },
  calculator: { enabled: true },
  language: { languageIds: ["lang-1"], defaultLanguageId: "lang-1" },
};

function renderBuilder() {
  render(
    <QueryClientProvider
      client={new QueryClient({
        defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
      })}
    >
      <BlueprintBuilder blueprintId="bp-1" versionId="bv-1" />
    </QueryClientProvider>,
  );
}

describe("BlueprintBuilder", () => {
  beforeEach(() => {
    apiGetMock.mockReset();
    apiPostMock.mockReset();
    apiGetMock.mockImplementation((url: string) => {
      if (url === "/blueprints/bp-1") {
        return Promise.resolve({
          data: {
            id: "bp-1",
            code: "SSC-BP",
            name: "SSC Blueprint",
            description: "desc",
            status: "ACTIVE",
            versions: [],
          },
          message: "ok",
        });
      }
      if (url === "/blueprints/bp-1/versions/bv-1") {
        return Promise.resolve({
          data: {
            id: "bv-1",
            blueprintId: "bp-1",
            version: 1,
            published: true,
            status: "PUBLISHED",
            configuration: storedConfiguration,
          },
          message: "ok",
        });
      }
      return Promise.resolve({ data: [], message: "ok" });
    });
  });

  it("maps a loaded configuration back into the editable form", async () => {
    renderBuilder();

    await waitFor(() => expect(screen.getByLabelText("Section 1 name")).toHaveValue("IBPS"));
    expect(screen.getByLabelText("Section 2 name")).toHaveValue("Quantitative Aptitude");
    expect(screen.getByLabelText("Section 1 question count")).toHaveValue(25);
    expect(screen.getByLabelText("Section 2 marks")).toHaveValue(70);
    expect(screen.getByLabelText("Section 2 duration minutes")).toHaveValue(40);
    expect(screen.getByLabelText("Navigation mode")).toHaveValue("SECTION_SEQUENTIAL");
    expect(screen.getByLabelText("Timer mode")).toHaveValue("SECTION");
    expect(screen.getByLabelText("Calculator enabled")).toBeChecked();
  });

  it("marks a published version immutable and offers create-new-version", async () => {
    renderBuilder();

    await waitFor(() => expect(screen.getByText(/published and immutable/)).toBeInTheDocument());
    expect(screen.getByRole("button", { name: "Publish" })).toBeDisabled();
    expect(
      screen.getByRole("button", { name: "Create new version from this configuration" }),
    ).toBeInTheDocument();
  });
});
