"use client";

import Link from "next/link";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { PageHeader } from "@/components/ui/page-header";
import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { useAuth } from "@/lib/auth/auth-context";

/**
 * Account settings ({@code specs/10-P1-Identity-V1.5.md} §103).
 *
 * <p>Rendered inside {@code RoleShell}, so whichever workspace the user actually works in supplies the
 * navigation. Before this, the page was a bare centred card reachable from the avatar menu with no
 * sidebar and no way back — it was the one screen in the authenticated application that did not look
 * like part of the application.
 */
export default function AccountPage() {
  const { user, logout } = useAuth();

  return (
    <div className="flex w-full max-w-3xl flex-col gap-6">
      <PageHeader
        eyebrow="Account"
        title="Your account"
        description="The details your organization holds for you, and your session."
      />

      <AuthenticatedGuard>
        <Card>
          <CardHeader>
            <CardTitle>Profile</CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="grid gap-4 text-sm sm:grid-cols-2">
              <div>
                <dt className="text-muted-foreground">Name</dt>
                <dd className="font-medium text-foreground">
                  {user?.firstName} {user?.lastName}
                </dd>
              </div>
              <div>
                <dt className="text-muted-foreground">Email</dt>
                <dd className="font-medium text-foreground">{user?.email}</dd>
              </div>
              <div className="sm:col-span-2">
                <dt className="text-muted-foreground">Roles</dt>
                <dd className="font-medium text-foreground">
                  {user?.roles.join(", ") || "—"}
                </dd>
              </div>
            </dl>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Security</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-wrap items-center gap-3">
            <Link
              href="/change-password"
              className="inline-flex h-10 items-center justify-center rounded-md border border-input bg-surface px-4 text-sm font-medium text-foreground transition-colors hover:bg-muted"
            >
              Change password
            </Link>
            <Button variant="ghost" onClick={() => void logout()}>
              Sign out
            </Button>
          </CardContent>
        </Card>
      </AuthenticatedGuard>
    </div>
  );
}
