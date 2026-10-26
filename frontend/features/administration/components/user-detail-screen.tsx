"use client";

import Link from "next/link";

import { describeAdminError, formatDateTime } from "@/components/admin/format";
import { StatusBadge } from "@/components/admin/status-badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useUser } from "@/features/administration/hooks/use-administration";
import { useAuth } from "@/lib/auth/auth-context";

/** Read a single user and offer edit ({@code docs/frontend/admin-flow.md} §6). */
export function UserDetailScreen({ userId }: { userId: string }) {
  const { can } = useAuth();
  const query = useUser(userId);

  if (query.isPending) {
    return <p role="status" className="text-sm text-muted-foreground">Loading user…</p>;
  }

  if (query.isError) {
    return (
      <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
 {describeAdminError(query.error, "The user could not be loaded.")}
        <button type="button" onClick={() => void query.refetch()} className="ml-3 underline">
          Retry
        </button>
      </div>
    );
  }

  const user = query.data;

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader>
          <div className="flex items-center gap-2">
            <CardTitle>{user.firstName + " " + user.lastName}</CardTitle>
            <StatusBadge status={user.status} />
          </div>
        </CardHeader>
        <CardContent>
          <dl className="grid gap-x-6 gap-y-2 text-sm sm:grid-cols-2">
            <div className="flex justify-between gap-2">
              <dt className="text-muted-foreground">Email</dt>
              <dd>{user.email}</dd>
            </div>
            <div className="flex justify-between gap-2">
              <dt className="text-muted-foreground">Mobile</dt>
              <dd>{user.mobile ?? "—"}</dd>
            </div>
            <div className="flex justify-between gap-2">
              <dt className="text-muted-foreground">Roles</dt>
              <dd>{user.roles.length > 0 ? user.roles.join(", ") : "No role"}</dd>
            </div>
            <div className="flex justify-between gap-2">
              <dt className="text-muted-foreground">Created</dt>
              <dd>{formatDateTime(user.createdOn) ?? "—"}</dd>
            </div>
          </dl>
        </CardContent>
      </Card>

      <div className="flex items-center gap-2">
 {can("USER_UPDATE") ? (
          <Link
 href={"/admin/users/" + userId + "/edit"}
 className="inline-flex h-10 items-center rounded-md bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-hover"
          >
            Edit user
          </Link>
        ) : null}
        <Link
 href="/admin/users"
 className="inline-flex h-10 items-center rounded-md border border-input px-4 py-2 text-sm hover:bg-muted"
        >
          Back to users
        </Link>
      </div>
    </div>
  );
}
