/**
 * UI permission helper ({@code specs/10-P1-Identity-V1.5.md §161, §162}).
 *
 * <p>This is a UI affordance only. Hiding a control is never authorization: the backend
 * independently enforces every permission on the API ({@code §101, §102}).
 */
export function can(permissions: readonly string[] | null | undefined, code: string): boolean {
  if (!permissions || permissions.length === 0) {
    return false;
  }
  return permissions.includes(code);
}
