import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/lib/api-client", () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  setUnauthorizedHandler: vi.fn(),
}));

import { ContactForm } from "@/features/public/contact-form";
import { apiPost } from "@/lib/api-client";

const mockedPost = vi.mocked(apiPost);

describe("ContactForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("requires name, email, and message", async () => {
    render(<ContactForm />);
    await userEvent.click(screen.getByRole("button", { name: /send message/i }));
    expect(await screen.findByRole("alert")).toHaveTextContent("required");
    expect(mockedPost).not.toHaveBeenCalled();
  });

  it("rejects an invalid email", async () => {
    render(<ContactForm />);
    await userEvent.type(screen.getByLabelText("Name"), "Ada");
    await userEvent.type(screen.getByLabelText("Email"), "not-an-email");
    await userEvent.type(screen.getByLabelText("Message"), "Hello");
    await userEvent.click(screen.getByRole("button", { name: /send message/i }));
    expect(await screen.findByRole("alert")).toHaveTextContent("valid email");
    expect(mockedPost).not.toHaveBeenCalled();
  });

  it("submits the payload and shows success", async () => {
    mockedPost.mockResolvedValue({ message: "ok", data: null });
    render(<ContactForm />);
    await userEvent.type(screen.getByLabelText("Name"), "Ada");
    await userEvent.type(screen.getByLabelText("Email"), "ada@example.com");
    await userEvent.type(screen.getByLabelText("Organization (optional)"), "Acme");
    await userEvent.type(screen.getByLabelText("Message"), "Hello");
    await userEvent.click(screen.getByRole("button", { name: /send message/i }));
    await waitFor(() =>
      expect(mockedPost).toHaveBeenCalledWith("/public/contact", {
        name: "Ada",
        email: "ada@example.com",
        message: "Hello",
        organization: "Acme",
      }),
    );
    expect(await screen.findByRole("status")).toHaveTextContent("be in touch");
  });

  it("shows the rate-limit message on 429", async () => {
    const { ApiClientError } = await import("@/lib/errors");
    mockedPost.mockRejectedValue(
      new ApiClientError({ message: "Too many requests", status: 429, code: "RATE_LIMITED" }),
    );
    render(<ContactForm />);
    await userEvent.type(screen.getByLabelText("Name"), "Ada");
    await userEvent.type(screen.getByLabelText("Email"), "ada@example.com");
    await userEvent.type(screen.getByLabelText("Message"), "Hello");
    await userEvent.click(screen.getByRole("button", { name: /send message/i }));
    expect(await screen.findByRole("alert")).toHaveTextContent(/too many requests/i);
  });
});
