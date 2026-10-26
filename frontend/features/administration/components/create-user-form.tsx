"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";

import { ApiClientError } from "@/lib/errors";
import { RoleCheckboxGroup } from "@/features/administration/components/role-checkbox-group";
import { useCreateUser, useRoles } from "@/features/administration/hooks/use-administration";
import { createUserSchema, type CreateUser } from "@/features/administration/schemas/administration";

const inputClass =
 "w-full rounded-md border border-input px-3 py-2 text-sm";

/** Create a non-privileged user ({@code docs/frontend/admin-flow.md} §5). */
export function CreateUserForm() {
  const router = useRouter();
  const mutation = useCreateUser();
  const rolesQuery = useRoles(true);
  const [serverError, setServerError] = useState<string | null>(null);
  const [roleError, setRoleError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateUser>({
 defaultValues: { firstName: "", lastName: "", email: "", mobile: "", roles: [] },
 });

 const selectedRoles = watch("roles");

  // ADMIN and SUPER_ADMIN are not in the creatable set (admin-flow §5); the backend re-checks.
  const creatableRoles = (rolesQuery.data?.items ?? []).filter(
    (role) => role.status === "ACTIVE" && role.code !== "ADMIN" && role.code !== "SUPER_ADMIN",
  );

  const submit = handleSubmit(async (raw) => {
 setServerError(null);
 setRoleError(null);
 const parsed = createUserSchema.safeParse(raw);
 if (!parsed.success) {
 for (const issue of parsed.error.issues) {
 const path = issue.path[0];
 if (path === "roles") {
 setRoleError(issue.message);
 } else if (path === "firstName" || path === "lastName" || path === "email" || path === "mobile") {
 setError(path, { message: issue.message });
 }
 }
 return;
 }
 try {
 const created = await mutation.mutateAsync(parsed.data);
 router.push("/admin/users/" + created.id);
 } catch (error) {
 setServerError(
 error instanceof ApiClientError ? error.message : "The user could not be created.",
      );
    }
  });

  return (
    <form onSubmit={submit} aria-label="Create user" className="space-y-4">
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
        <div className="space-y-1">
          <label htmlFor="email" className="text-sm font-medium">Email</label>
          <input id="email" type="email" {...register("email")} className={inputClass} />
          {errors.email ? (
            <p role="alert" className="text-sm text-red-600">{errors.email.message}</p>
          ) : null}
        </div>
        <div className="space-y-1">
          <label htmlFor="mobile" className="text-sm font-medium">Mobile (optional)</label>
          <input id="mobile" {...register("mobile")} className={inputClass} />
          {errors.mobile ? (
            <p role="alert" className="text-sm text-red-600">{errors.mobile.message}</p>
          ) : null}
        </div>
      </div>

      <RoleCheckboxGroup
        roles={creatableRoles}
        selected={selectedRoles}
        onChange={(next) => setValue("roles", next)}
      />
      {roleError ? <p role="alert" className="text-sm text-red-600">{roleError}</p> : null}

      {serverError ? <p role="alert" className="text-sm text-red-600">{serverError}</p> : null}

      <div className="flex items-center gap-2">
        <button
 type="submit"
 disabled={isSubmitting || mutation.isPending}
 className="rounded bg-primary px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
 {isSubmitting ? "Creating…" : "Create and send invite"}
        </button>
        <button
 type="button"
          onClick={() => router.push("/admin/users")}
 className="rounded border border-input px-4 py-2 text-sm"
        >
          Cancel
        </button>
      </div>
    </form>
  );
}
