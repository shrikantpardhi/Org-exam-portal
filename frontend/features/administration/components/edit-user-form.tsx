"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";

import { describeAdminError } from "@/components/admin/format";
import { ApiClientError } from "@/lib/errors";
import { useAuth } from "@/lib/auth/auth-context";
import { RoleCheckboxGroup } from "@/features/administration/components/role-checkbox-group";
import { useRoles, useUpdateUser, useUser } from "@/features/administration/hooks/use-administration";
import {
 updateUserSchema,
 type RoleSummary,
 type UpdateUser,
} from "@/features/administration/schemas/administration";

const inputClass =
 "w-full rounded-md border border-input px-3 py-2 text-sm";

/** Edit a user's profile and roles ({@code docs/frontend/admin-flow.md} §7). */
export function EditUserForm({ userId }: { userId: string }) {
  const userQuery = useUser(userId);
  const rolesQuery = useRoles(true);

  if (userQuery.isPending || rolesQuery.isPending) {
    return <p role="status" className="text-sm text-muted-foreground">Loading user…</p>;
  }

  if (userQuery.isError) {
    return (
      <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
 {describeAdminError(userQuery.error, "The user could not be loaded.")}
        <button type="button" onClick={() => void userQuery.refetch()} className="ml-3 underline">
          Retry
        </button>
      </div>
    );
  }

  const user = userQuery.data;
  // SUPER_ADMIN is immutable; the backend also blocks assigning it through update.
  const assignableRoles = (rolesQuery.data?.items ?? []).filter(
    (role) => role.status === "ACTIVE" && role.code !== "SUPER_ADMIN",
  );
  const initialRoleIds = assignableRoles
    .filter((role) => user.roles.includes(role.code))
    .map((role) => role.id);

  return (
    <EditUserFormFields
 userId={userId}
 email={user.email}
 firstName={user.firstName}
 lastName={user.lastName}
 mobile={user.mobile ?? ""}
      roleIds={initialRoleIds}
      roles={assignableRoles}
    />
  );
}

function EditUserFormFields({
  userId,
  email,
  firstName,
  lastName,
  mobile,
  roleIds,
  roles,
}: {
  userId: string;
  email: string;
  firstName: string;
  lastName: string;
  mobile: string;
  roleIds: string[];
  roles: RoleSummary[];
}) {
  const router = useRouter();
  const { user: me } = useAuth();
  const mutation = useUpdateUser(userId);
  const [serverError, setServerError] = useState<string | null>(null);
  const [roleError, setRoleError] = useState<string | null>(null);

  const isSelf = me?.id === userId;

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<UpdateUser>({
 defaultValues: { firstName, lastName, mobile, roles: roleIds },
 });

 const selectedRoles = watch("roles");

  const submit = handleSubmit(async (raw) => {
 setServerError(null);
 setRoleError(null);
 const parsed = updateUserSchema.safeParse(raw);
 if (!parsed.success) {
 for (const issue of parsed.error.issues) {
 const path = issue.path[0];
 if (path === "roles") {
 setRoleError(issue.message);
 } else if (path === "firstName" || path === "lastName" || path === "mobile") {
 setError(path, { message: issue.message });
 }
 }
 return;
 }
 try {
 await mutation.mutateAsync(parsed.data);
 router.push("/admin/users/" + userId);
 } catch (error) {
 setServerError(
 error instanceof ApiClientError ? error.message : "The user could not be updated.",
      );
    }
  });

  return (
    <form onSubmit={submit} aria-label="Edit user" className="space-y-4">
      <div className="space-y-1">
        <p className="text-sm font-medium">Email</p>
        <p className="rounded-md border border-border bg-muted px-3 py-2 text-sm text-muted-foreground">
          {email}
        </p>
        <p className="text-xs text-muted-foreground">Email is the login identifier and cannot change.</p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-1">
          <label htmlFor="firstName" className="text-sm font-medium">First name</label>
          <input id="firstName" {...register("firstName")} className={inputClass} />
          {errors.firstName ? (
            <p role="alert" className="text-sm text-red-600">{errors.firstName.message}</p>
          ) : null}
        </div>
        <div className="space-y-1">
          <label htmlFor="lastName" className="text-sm font-medium">Last name</label>
          <input id="lastName" {...register("lastName")} className={inputClass} />
          {errors.lastName ? (
            <p role="alert" className="text-sm text-red-600">{errors.lastName.message}</p>
          ) : null}
        </div>
      </div>

      <div className="space-y-1">
        <label htmlFor="mobile" className="text-sm font-medium">Mobile (optional)</label>
        <input id="mobile" {...register("mobile")} className={inputClass} />
        {errors.mobile ? (
          <p role="alert" className="text-sm text-red-600">{errors.mobile.message}</p>
        ) : null}
      </div>

      <RoleCheckboxGroup
        roles={roles}
        selected={selectedRoles}
        onChange={(next) => setValue("roles", next)}
        disabled={isSelf}
      />
      {isSelf ? (
        <p role="note" className="text-sm text-muted-foreground">You cannot change your own roles.</p>
      ) : null}
      {roleError ? <p role="alert" className="text-sm text-red-600">{roleError}</p> : null}

      {serverError ? <p role="alert" className="text-sm text-red-600">{serverError}</p> : null}

      <div className="flex items-center gap-2">
        <button
 type="submit"
 disabled={isSubmitting || mutation.isPending}
 className="rounded bg-primary px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
 {isSubmitting ? "Saving…" : "Save changes"}
        </button>
        <button
 type="button"
          onClick={() => router.push("/admin/users/" + userId)}
 className="rounded border border-input px-4 py-2 text-sm"
        >
          Cancel
        </button>
      </div>
    </form>
  );
}
