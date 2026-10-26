/** Post-login routing ({@code specs/05-API-Contract-V1.5.md §21.1}; ADR-0035). */
export function roleHome(roles: string[], mustChangePassword?: boolean | null): string {
  if (mustChangePassword) {
    return "/change-password";
  }
  if (roles.includes("SUPER_ADMIN")) {
    return "/platform";
  }
  if (roles.includes("ADMIN")) {
    return "/admin";
  }
  if (roles.includes("STUDENT")) {
    return "/student";
  }
  return "/account";
}

/** A safe, same-origin relative redirect target, or null when the value is unsafe. */
export function safeNext(value: string | null | undefined): string | null {
  if (!value) {
    return null;
  }
  if (value.startsWith("/") && !value.startsWith("//")) {
    return value;
  }
  return null;
}
