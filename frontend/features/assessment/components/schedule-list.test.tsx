import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ScheduleList } from "@/features/assessment/components/schedule-list";
import { apiGet, apiPost, apiPut } from "@/lib/api-client";

vi.mock("@/lib/api-client", () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiPut: vi.fn(),
  apiDelete: vi.fn(),
  setUnauthorizedHandler: vi.fn(),
}));

const apiGetMock = vi.mocked(apiGet);
const apiPostMock = vi.mocked(apiPost);
const apiPutMock = vi.mocked(apiPut);

/**
 * Schedule authoring ({@code specs/13-P4-Assessment-V1.5.md §109}).
 *
 * <p>Local `datetime-local` values become ISO instants at the API boundary, and an end before the start
 * is rejected in the form before any request is issued.
 */
const assessment = {
  id: "a-1",
  code: "SSC-MOCK",
  name: "SSC CGL Mock 1",
  status: "ACTIVE",
  versions: [
    {
      id: "av-1",
      assessmentId: "a-1",
      blueprintVersionId: "bv-1",
      version: 1,
      totalQuestions: 10,
      totalMarks: 10,
      durationMinutes: 60,
      engineType: "NTA",
      published: true,
    },
  ],
};

function renderSchedules() {
  render(
    <QueryClientProvider
      client={new QueryClient({
        defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
      })}
    >
      <ScheduleList />
    </QueryClientProvider>,
  );
}

describe("ScheduleList", () => {
  beforeEach(() => {
    apiGetMock.mockReset();
    apiPostMock.mockReset();
    apiPutMock.mockReset();
    apiGetMock.mockImplementation((url: string) => {
      if (url.startsWith("/assessments/a-1")) {
        return Promise.resolve({ data: assessment, message: "ok" });
      }
      if (url.startsWith("/assessments")) {
        return Promise.resolve({
          data: [{ id: "a-1", code: "SSC-MOCK", name: "SSC CGL Mock 1", status: "ACTIVE" }],
          message: "ok",
        });
      }
      if (url.startsWith("/assessment-schedules")) {
        return Promise.resolve({ data: [], message: "ok" });
      }
      return Promise.resolve({ data: [], message: "ok" });
    });
    apiPostMock.mockResolvedValue({
      data: {
        id: "sch-1",
        assessmentVersionId: "av-1",
        mode: "EXAM",
        startTime: "2026-10-22T09:00:00.000Z",
        endTime: "2026-10-22T12:00:00.000Z",
        status: "SCHEDULED",
      },
      message: "created",
    });
  });

  it("converts local datetime input to an ISO instant on create", async () => {
    renderSchedules();

    await screen.findByRole("option", { name: "SSC CGL Mock 1" });
    await userEvent.selectOptions(screen.getByLabelText("Assessment"), "a-1");

    await waitFor(() => expect(screen.getByText("Version 1 (published)")).toBeInTheDocument());
    await userEvent.selectOptions(screen.getByLabelText("Assessment version"), "av-1");

    await userEvent.type(screen.getByLabelText("Start time"), "2026-10-22T09:00");
    await userEvent.type(screen.getByLabelText("End time"), "2026-10-22T12:00");
    await userEvent.click(screen.getByRole("button", { name: "Create schedule" }));

    await waitFor(() => expect(apiPostMock).toHaveBeenCalledTimes(1));

    const [path, body] = apiPostMock.mock.calls[0]!;
    expect(path).toBe("/assessment-schedules");
    const sent = body as { assessmentVersionId: string; startTime: string; endTime: string; mode: string };
    expect(sent.assessmentVersionId).toBe("av-1");
    expect(sent.mode).toBe("EXAM");
    // ISO-8601 instants, not zoneless local strings.
    expect(sent.startTime).toBe(new Date("2026-10-22T09:00").toISOString());
    expect(sent.endTime).toBe(new Date("2026-10-22T12:00").toISOString());
    expect(sent.startTime).toMatch(/Z$/);
  });

  it("rejects an end before the start without calling the API", async () => {
    renderSchedules();

    await screen.findByRole("option", { name: "SSC CGL Mock 1" });
    await userEvent.selectOptions(screen.getByLabelText("Assessment"), "a-1");
    await waitFor(() => expect(screen.getByText("Version 1 (published)")).toBeInTheDocument());
    await userEvent.selectOptions(screen.getByLabelText("Assessment version"), "av-1");

    await userEvent.type(screen.getByLabelText("Start time"), "2026-10-22T12:00");
    await userEvent.type(screen.getByLabelText("End time"), "2026-10-22T09:00");
    await userEvent.click(screen.getByRole("button", { name: "Create schedule" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "End time must be after the start time.",
    );
    expect(apiPostMock).not.toHaveBeenCalled();
  });

  it("requires a chosen version before submitting", async () => {
    renderSchedules();

    await screen.findByRole("option", { name: "SSC CGL Mock 1" });
    await userEvent.type(screen.getByLabelText("Start time"), "2026-10-22T09:00");
    await userEvent.type(screen.getByLabelText("End time"), "2026-10-22T12:00");
    await userEvent.click(screen.getByRole("button", { name: "Create schedule" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Choose an assessment version.");
    expect(apiPostMock).not.toHaveBeenCalled();
  });

  it("sends the window update through the frozen PUT path", async () => {
    apiGetMock.mockImplementation((url: string) => {
      if (url.startsWith("/assessment-schedules")) {
        return Promise.resolve({
          data: [
            {
              id: "sch-1",
              assessmentVersionId: "av-1",
              mode: "EXAM",
              startTime: "2026-10-22T09:00:00.000Z",
              endTime: "2026-10-22T12:00:00.000Z",
              status: "SCHEDULED",
            },
          ],
          message: "ok",
        });
      }
      return Promise.resolve({ data: [], message: "ok" });
    });
    apiPutMock.mockResolvedValue({ data: {}, message: "ok" });

    renderSchedules();

    await waitFor(() => expect(screen.getByRole("button", { name: "Save window" })).toBeInTheDocument());
    await userEvent.click(screen.getByRole("button", { name: "Save window" }));

    await waitFor(() => expect(apiPutMock).toHaveBeenCalledTimes(1));
    const [path, body] = apiPutMock.mock.calls[0]!;
    expect(path).toBe("/assessment-schedules/sch-1");
    expect(body).toEqual({
      startTime: "2026-10-22T09:00:00.000Z",
      endTime: "2026-10-22T12:00:00.000Z",
    });
  });
});
