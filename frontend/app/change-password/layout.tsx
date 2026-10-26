import type { ReactNode } from "react";

/**
 * The forced-password-change workspace ({@code specs/05 §72.1}; ADR-0034).
 *
 * <p>Deliberately outside the normal shells. Until the flag clears the backend refuses every
 * {@code /api/v1} resource except change-password, logout, and the current-user endpoints, so a
 * sidebar of links would offer a user nothing but 403s. The header keeps the product identity and a
 * way out; the content is a single centred card, consistent with the other authentication screens.
 *
 * <p>Inside the shell (a user who chose to change their password voluntarily) this route is reached
 * from the avatar menu, and the same focused layout is still the right one for the task.
 */
export default function ChangePasswordLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col bg-background">
      <header className="border-b border-border bg-surface">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-6">
          <span className="text-lg font-semibold tracking-tight text-foreground">Exam Platform</span>
        </div>
      </header>
      <main id="main-content" className="flex flex-1 items-start justify-center px-6 py-12 sm:items-center">
        <div className="w-full max-w-md">{children}</div>
      </main>
    </div>
  );
}
