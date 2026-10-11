"use client";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { useAuth } from "@/lib/auth/auth-context";

/**
 * Example protected page ({@code specs/10-P1-Identity-V1.5.md §103}).
 *
 * <p>Demonstrates the protected-route pattern future business modules reuse: the guard checks
 * authentication, the page loads current user context, and logout notifies the backend.
 */
export default function AccountPage() {
  const { user, logout } = useAuth();

  return (
    <main className="mx-auto flex w-full max-w-md flex-col gap-6 px-6 py-16">
      <AuthenticatedGuard>
        <Card>
          <CardHeader>
            <CardTitle>Your account</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3 text-sm">
            <p>
              {user?.firstName} {user?.lastName}
            </p>
            <p className="text-slate-500">{user?.email}</p>
            <p className="text-slate-500">Roles: {user?.roles.join(", ") || "—"}</p>
            <Button variant="outline" onClick={() => void logout()}>
              Sign out
            </Button>
          </CardContent>
        </Card>
      </AuthenticatedGuard>
    </main>
  );
}
