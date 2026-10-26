import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";

import { ApiReference } from "@/components/docs/api-reference";
import type { ApiEndpoint, ApiTag } from "@/lib/openapi";

const tags: ApiTag[] = [
  { name: "Users", description: "User management" },
  { name: "Auth", description: "" },
];

const endpoints: ApiEndpoint[] = [
  { method: "GET", path: "/api/v1/users", summary: "List users", description: "", tag: "Users", parameters: [], requestBodySchema: null },
  {
    method: "POST",
    path: "/api/v1/auth/login",
    summary: "Sign in",
    description: "Authenticate",
    tag: "Auth",
    parameters: [{ name: "portal", in: "query", required: true, description: "" }],
    requestBodySchema: "LoginRequest",
  },
];

describe("api reference", () => {
  it("groups endpoints by tag and renders method, path and summary", () => {
    render(<ApiReference endpoints={endpoints} tags={tags} />);

    expect(screen.getByRole("heading", { name: "Users" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Auth" })).toBeInTheDocument();
    expect(screen.getByText("/api/v1/users")).toBeInTheDocument();
    expect(screen.getByText("POST")).toBeInTheDocument();
    expect(screen.getByText("2 endpoints")).toBeInTheDocument();
  });

  it("filters the endpoint list by query", async () => {
    const user = userEvent.setup();
    render(<ApiReference endpoints={endpoints} tags={tags} />);

    await user.type(screen.getByLabelText("Filter API endpoints"), "login");

    expect(screen.getByText("/api/v1/auth/login")).toBeInTheDocument();
    expect(screen.queryByText("/api/v1/users")).toBeNull();
    expect(screen.getByText("1 endpoint")).toBeInTheDocument();
  });
});
