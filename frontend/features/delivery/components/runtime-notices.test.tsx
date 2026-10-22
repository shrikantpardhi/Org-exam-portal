import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { QuestionPalette } from "@/features/delivery/components/question-palette";
import { SectionNavigator } from "@/features/delivery/components/section-navigator";
import {
  ExpiredNotice,
  OfflineIndicator,
  SaveIndicator,
  SecondTabNotice,
  SessionRecoveryBanner,
  SubmissionDialog,
} from "@/features/delivery/components/runtime-notices";
import { paletteCounts } from "@/features/delivery/lib/palette";

/**
 * Runtime presentation tests ({@code specs/14-P5-Delivery-V1.5.md §130-§134}).
 *
 * <p>These assert what the student actually sees: that the palette is readable without colour, that a
 * failed save is never presented as saved, that the offline indicator explains what happens to their
 * answers, and that the submission dialog reports answered/unanswered counts without revealing any
 * correctness.
 */

const COUNTS = paletteCounts(["Answered", "NotVisited", "MarkedForReview", "Answered"]);

describe("question palette", () => {
  it("renders a numbered button per question", () => {
    render(<QuestionPalette statuses={["Answered", "NotVisited", "MarkedForReview"]} currentIndex={0} onJump={() => {}} />);
    expect(screen.getByRole("button", { name: /question 1/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /question 3/i })).toBeInTheDocument();
  });

  it("puts the status in the accessible name so colour is not the only signal", () => {
    render(<QuestionPalette statuses={["Answered", "MarkedForReview"]} currentIndex={0} onJump={() => {}} />);
    expect(screen.getByRole("button", { name: "Question 2, Marked for review" })).toBeInTheDocument();
  });

  it("reports the clicked index", async () => {
    const onJump = vi.fn();
    render(<QuestionPalette statuses={["Answered", "NotVisited"]} currentIndex={0} onJump={onJump} />);
    await userEvent.click(screen.getByRole("button", { name: /question 2/i }));
    expect(onJump).toHaveBeenCalledWith(1);
  });

  it("marks the current question and disables navigation when read-only", () => {
    render(<QuestionPalette statuses={["Answered", "NotVisited"]} currentIndex={1} onJump={() => {}} disabled />);
    expect(screen.getByRole("button", { name: /question 1/i })).toBeDisabled();
    expect(screen.getByRole("button", { name: /question 2/i })).toHaveAttribute("aria-current", "true");
  });
});

describe("section navigator", () => {
  it("jumps to the first question of the chosen section", async () => {
    const onJump = vi.fn();
    render(
      <SectionNavigator
        sections={[
          { section: "Reasoning", ids: ["qv-1", "qv-2"] },
          { section: "Quantitative Aptitude", ids: ["qv-3"] },
        ]}
        currentQuestionId="qv-3"
        onJump={onJump}
      />,
    );
    await userEvent.click(screen.getByRole("button", { name: /reasoning/i }));
    expect(onJump).toHaveBeenCalledWith("qv-1");
  });

  it("shows the question count per section and the active section", () => {
    render(
      <SectionNavigator
        sections={[{ section: "Reasoning", ids: ["qv-1", "qv-2"] }]}
        currentQuestionId="qv-1"
        onJump={() => {}}
      />,
    );
    expect(screen.getByText("(2)")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /reasoning/i })).toHaveAttribute("aria-current", "true");
  });
});

describe("save indicator", () => {
  it("says saving while a save is in flight", () => {
    render(<SaveIndicator saving savedAt={null} failed={0} offline={false} />);
    expect(screen.getByRole("status")).toHaveTextContent(/saving/i);
  });

  it("reports saved with a timestamp once acknowledged", () => {
    render(<SaveIndicator saving={false} savedAt="10:30" failed={0} offline={false} />);
    expect(screen.getByRole("status")).toHaveTextContent("Saved 10:30");
  });

  it("never reports a failed save as saved", () => {
    render(<SaveIndicator saving={false} savedAt="10:30" failed={2} offline={false} />);
    const status = screen.getByRole("status");
    expect(status).toHaveTextContent(/not saved/i);
    expect(status).not.toHaveTextContent(/^Saved/);
  });

  it("prefers the offline explanation over a stale saved timestamp", () => {
    render(<SaveIndicator saving={false} savedAt="10:30" failed={0} offline />);
    expect(screen.getByRole("status")).toHaveTextContent(/offline/i);
  });
});

describe("offline indicator", () => {
  it("explains that answers are kept locally and reassures about closing", () => {
    render(<OfflineIndicator pending={3} failed={0} />);
    expect(screen.getByRole("status")).toHaveTextContent(/offline/i);
    expect(screen.getByRole("status")).toHaveTextContent(/3 answer/i);
    expect(screen.getByRole("status")).toHaveTextContent(/do not close/i);
  });

  it("surfaces answers that could not be sent", () => {
    render(<OfflineIndicator pending={0} failed={1} />);
    expect(screen.getByRole("status")).toHaveTextContent(/could not be sent/i);
  });
});

describe("second tab and recovery notices", () => {
  it("explains that another tab owns the examination", () => {
    render(<SecondTabNotice />);
    expect(screen.getByRole("alert")).toHaveTextContent(/open in another tab/i);
    expect(screen.getByRole("alert")).toHaveTextContent(/read-only/i);
  });

  it("confirms a recovered session and can be dismissed", async () => {
    const onDismiss = vi.fn();
    render(<SessionRecoveryBanner onDismiss={onDismiss} />);
    expect(screen.getByRole("status")).toHaveTextContent(/recovered/i);
    await userEvent.click(screen.getByRole("button", { name: /dismiss/i }));
    expect(onDismiss).toHaveBeenCalled();
  });

  it("distinguishes an expired attempt that has and has not been submitted", () => {
    const { rerender } = render(<ExpiredNotice submitted={false} />);
    // Not yet submitted: the copy tells the student the submission is still in progress.
    expect(screen.getByRole("alert")).toHaveTextContent(/being submitted now/i);
    expect(screen.getByRole("alert")).not.toHaveTextContent(/has been submitted/i);
    rerender(<ExpiredNotice submitted />);
    // Submitted: the copy reports completion instead of promising it.
    expect(screen.getByRole("alert")).toHaveTextContent(/has been submitted/i);
    expect(screen.getByRole("alert")).not.toHaveTextContent(/being submitted now/i);
  });
});

describe("submission dialog", () => {
  it("shows answered and unanswered counts", () => {
    render(
      <SubmissionDialog counts={COUNTS} allowCancel submitting={false} onConfirm={() => {}} onCancel={() => {}} />,
    );
    const dialog = screen.getByRole("dialog");
    expect(dialog).toHaveTextContent("4"); // total
    expect(dialog).toHaveTextContent("2"); // answered
    expect(dialog).toHaveTextContent(/not answered/i);
    expect(dialog).toHaveTextContent(/marked for review/i);
  });

  it("warns when questions are unanswered", () => {
    render(
      <SubmissionDialog counts={COUNTS} allowCancel submitting={false} onConfirm={() => {}} onCancel={() => {}} />,
    );
    expect(screen.getByRole("note")).toHaveTextContent(/unanswered question/i);
  });

  it("never reveals a score or correctness", () => {
    render(
      <SubmissionDialog counts={COUNTS} allowCancel submitting={false} onConfirm={() => {}} onCancel={() => {}} />,
    );
    const text = screen.getByRole("dialog").textContent?.toLowerCase() ?? "";
    expect(text).not.toContain("correct");
    expect(text).not.toContain("score");
    expect(text).not.toContain("marks obtained");
  });

  it("confirms and cancels through the provided handlers", async () => {
    const onConfirm = vi.fn();
    const onCancel = vi.fn();
    render(
      <SubmissionDialog counts={COUNTS} allowCancel submitting={false} onConfirm={onConfirm} onCancel={onCancel} />,
    );
    await userEvent.click(screen.getByRole("button", { name: /^submit$/i }));
    expect(onConfirm).toHaveBeenCalled();
    await userEvent.click(screen.getByRole("button", { name: /keep working/i }));
    expect(onCancel).toHaveBeenCalled();
  });

  it("disables both actions while the submission is in flight", () => {
    render(
      <SubmissionDialog counts={COUNTS} allowCancel submitting onConfirm={() => {}} onCancel={() => {}} />,
    );
    expect(screen.getByRole("button", { name: /submitting/i })).toBeDisabled();
    expect(screen.getByRole("button", { name: /keep working/i })).toBeDisabled();
  });
});
