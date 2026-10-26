/**
 * Allow-listed table sorting for administration ({@code specs/20-P11-Administration-V1.5.md §65}).
 *
 * <p>{@code §65} requires server-side pagination, server-side filtering and *allow-listed* sorting.
 * The allow-list is the set of fields each frozen endpoint publishes through {@code QuerySupport}
 * (for example {@code UserController.SORTABLE}). A field outside the list must never reach the
 * request, because the backend rejects it — so the UI only ever offers, and only ever sends, a
 * field that the owning controller declared.
 */

export type SortDirection = "asc" | "desc";

export interface SortState {
  field: string;
  direction: SortDirection;
}

/** The fields each frozen admin list endpoint allows. Mirrors the backend allow-lists. */
export const ADMIN_SORTABLE_FIELDS = {
  users: ["createdOn", "email", "firstName", "lastName", "status"],
  roles: ["code", "name", "status", "createdOn"],
  permissions: ["code", "name", "status", "createdOn"],
  audit: ["createdOn", "action", "entityName"],
  organizations: ["code", "name", "status", "createdOn"],
} as const;

export type AdminSortResource = keyof typeof ADMIN_SORTABLE_FIELDS;

/** True when {@code field} is published by the owning controller's allow-list. */
export function isSortableField(resource: AdminSortResource, field: string): boolean {
  return (ADMIN_SORTABLE_FIELDS[resource] as readonly string[]).includes(field);
}

/**
 * Cycles ascending -> descending -> unsorted for the clicked field.
 *
 * <p>Rejects a field outside the allow-list by returning {@code null} rather than sending it, so a
 * programmer error cannot become a rejected request.
 */
export function toggleSort(
  current: SortState | null,
  resource: AdminSortResource,
  field: string,
): SortState | null {
  if (!isSortableField(resource, field)) {
    return null;
  }
  if (!current || current.field !== field) {
    return { field, direction: "asc" };
  }
  if (current.direction === "asc") {
    return { field, direction: "desc" };
  }
  return null;
}

/**
 * Renders the Spring {@code Pageable} {@code sort} parameter ({@code field,asc}).
 *
 * <p>Returns {@code undefined} for an absent or non-allow-listed sort so the request carries no
 * sort at all and the server's documented default ordering applies.
 */
export function toSortParam(
  sort: SortState | null | undefined,
  resource: AdminSortResource,
): string | undefined {
  if (!sort || !isSortableField(resource, sort.field)) {
    return undefined;
  }
  return `${sort.field},${sort.direction}`;
}

/** The {@code aria-sort} value for a header cell. */
export function ariaSortFor(
  sort: SortState | null | undefined,
  field: string,
): "ascending" | "descending" | "none" {
  if (!sort || sort.field !== field) {
    return "none";
  }
  return sort.direction === "asc" ? "ascending" : "descending";
}
