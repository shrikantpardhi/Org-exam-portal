/**
 * The single application token store ({@code specs/10-P1-Identity-V1.5.md §99, §157}).
 *
 * <p>Choice: the access token is kept in {@code sessionStorage}, in exactly one place. This is
 * deliberate — one documented strategy rather than a token duplicated across localStorage,
 * sessionStorage, and cookies. The token is never written to logs or analytics. A future
 * hardening step is an httpOnly cookie, which requires an API transport change and is recorded in
 * {@code docs/decisions/ADR-0010-p1-token-transport-and-storage.md}.
 *
 * <p>A memory fallback keeps the module safe outside the browser (for example, during server
 * rendering and in tests without a DOM storage implementation).
 */
export const ACCESS_TOKEN_STORAGE_KEY = "exam.auth.accessToken";

let memoryToken: string | null = null;

function sessionStorageOrNull(): Storage | null {
  try {
    if (typeof window !== "undefined" && window.sessionStorage) {
      return window.sessionStorage;
    }
  } catch {
    // Storage may be unavailable (privacy mode); fall back to memory.
  }
  return null;
}

export function getAccessToken(): string | null {
  const storage = sessionStorageOrNull();
  if (storage) {
    return storage.getItem(ACCESS_TOKEN_STORAGE_KEY);
  }
  return memoryToken;
}

export function setAccessToken(token: string): void {
  memoryToken = token;
  sessionStorageOrNull()?.setItem(ACCESS_TOKEN_STORAGE_KEY, token);
}

export function clearAccessToken(): void {
  memoryToken = null;
  sessionStorageOrNull()?.removeItem(ACCESS_TOKEN_STORAGE_KEY);
}
