import { describe, expect, it } from "vitest";

import {
  ADMIN_SORTABLE_FIELDS,
  ariaSortFor,
  isSortableField,
  toSortParam,
  toggleSort,
} from "@/components/admin/sorting";

/**
 * Allow-listed server sorting ({@code specs/20-P11-Administration-V1.5.md §65}).
 *
 * <p>The allow-list mirrors the frozen backend {@code QuerySupport} sets. A field outside it must
 * never be sent, because the backend rejects the whole request.
 */
describe("allow-listed table sorting", () => {
  it("matches the fields the frozen controllers publish", () => {
    expect(ADMIN_SORTABLE_FIELDS.users).toContain("email");
    expect(ADMIN_SORTABLE_FIELDS.users).toContain("status");
    expect(ADMIN_SORTABLE_FIELDS.roles).toContain("code");
    expect(ADMIN_SORTABLE_FIELDS.permissions).toContain("name");
    expect(ADMIN_SORTABLE_FIELDS.audit).toEqual(["createdOn", "action", "entityName"]);
  });

  it("rejects a field the endpoint does not allow", () => {
    expect(isSortableField("audit", "remarks")).toBe(false);
    expect(isSortableField("audit", "createdOn")).toBe(true);
    expect(isSortableField("users", "email")).toBe(true);
    // roles are allow-listed by code/name/status/createdOn only
    expect(isSortableField("roles", "description")).toBe(false);
  });

  it("cycles ascending, descending, then unsorted", () => {
    const first = toggleSort(null, "audit", "action");
    expect(first).toEqual({ field: "action", direction: "asc" });

    const second = toggleSort(first, "audit", "action");
    expect(second).toEqual({ field: "action", direction: "desc" });

    expect(toggleSort(second, "audit", "action")).toBeNull();
  });

  it("restarts ascending when the field changes", () => {
    const current = { field: "action", direction: "desc" } as const;
    expect(toggleSort(current, "audit", "createdOn")).toEqual({
      field: "createdOn",
      direction: "asc",
    });
  });

  it("refuses to produce a sort for a non-allow-listed field", () => {
    expect(toSortParam({ field: "remarks", direction: "asc" }, "audit")).toBeUndefined();
    expect(toSortParam(null, "audit")).toBeUndefined();
    expect(toSortParam(undefined, "audit")).toBeUndefined();
  });

  it("formats the Spring Pageable sort value for an allowed field", () => {
    expect(toSortParam({ field: "createdOn", direction: "desc" }, "audit")).toBe("createdOn,desc");
    expect(toSortParam({ field: "email", direction: "asc" }, "users")).toBe("email,asc");
  });

  it("reports aria-sort only for the active column", () => {
    const sort = { field: "action", direction: "desc" } as const;
    expect(ariaSortFor(sort, "action")).toBe("descending");
    expect(ariaSortFor(sort, "createdOn")).toBe("none");
    expect(ariaSortFor(null, "action")).toBe("none");
  });
});
