import { describe, expect, it } from "vitest";

import { flattenOpenApi } from "@/lib/openapi";

describe("flattenOpenApi", () => {
  it("flattens paths into sorted, tagged endpoints with parameters and request bodies", () => {
    const reference = flattenOpenApi({
      tags: [
        { name: "Users", description: "User management" },
        { name: "Auth" },
      ],
      paths: {
        "/api/v1/users": {
          post: {
            tags: ["Users"],
            summary: "Create a user",
            description: "Creates a user in the organization",
            requestBody: {
              content: { "application/json": { schema: { $ref: "#/components/schemas/CreateUserRequest" } } },
            },
          },
        },
        "/api/v1/auth/login": {
          post: {
            tags: ["Auth"],
            operationId: "login",
            parameters: [{ name: "portal", in: "query", required: true, description: "Login portal" }],
          },
        },
      },
    });

    expect(reference.tags.map((tag) => tag.name)).toEqual(["Users", "Auth"]);
    expect(reference.endpoints).toHaveLength(2);

    const create = reference.endpoints.find((endpoint) => endpoint.path === "/api/v1/users");
    expect(create?.method).toBe("POST");
    expect(create?.tag).toBe("Users");
    expect(create?.requestBodySchema).toBe("CreateUserRequest");

    const login = reference.endpoints.find((endpoint) => endpoint.path === "/api/v1/auth/login");
    expect(login?.summary).toBe("login");
    expect(login?.parameters).toEqual([
      { name: "portal", in: "query", required: true, description: "Login portal" },
    ]);
  });

  it("falls back to a default tag for untagged operations", () => {
    const reference = flattenOpenApi({
      paths: { "/api/v1/ping": { get: { operationId: "ping" } } },
    });

    expect(reference.endpoints[0]?.tag).toBe("default");
    expect(reference.endpoints[0]?.summary).toBe("ping");
  });
});
