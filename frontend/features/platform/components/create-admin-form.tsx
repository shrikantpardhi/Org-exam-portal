"use client";

import Link from "next/link";
import { useState } from "react";
import { useForm } from "react-hook-form";

import { ApiClientError } from "@/lib/errors";
import { useCreateOrganizationAdmin } from "@/features/platform/hooks/use-platform";
import { createAdminSchema, type CreateAdmin } from "@/features/platform/schemas/platform";

const inputClass = "w-full rounded-md border border-input bg-surface px-3 py-2 text-sm";

/**
 * Create the organization ADMIN (super-admin-flow §9; V37, ADR-0054).
 *
 * <p>Built on React Hook Form directly rather than on the shared {@code AdminForm}, because the
 * primary-administrator choice is a checkbox with its own help text and its own submit semantics
 * (the two-state field does not fit a flat list of text inputs). The error handling and the
 * {@code role="alert"} treatment match the shared form.
 */
export function CreateAdminForm({ organizationId }: { organizationId: string }) {
  const mutation = useCreateOrganizationAdmin(organizationId);
  const [invited, setInvited] = useState<{ email: string; isPrimary: boolean } | null>(null);
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CreateAdmin>({
    defaultValues: {
      firstName: "",
      lastName: "",
      email: "",
      mobile: "",
      makePrimary: false,
    },
  });

  const submit = handleSubmit(async (raw) => {
    setServerError(null);
    const parsed = createAdminSchema.safeParse(raw);
    if (!parsed.success) {
      for (const issue of parsed.error.issues) {
        const path = issue.path[0];
        if (
          path === "firstName" ||
          path === "lastName" ||
          path === "email" ||
          path === "mobile"
        ) {
          setError(path, { message: issue.message });
        }
      }
      return;
    }
    try {
      const result = await mutation.mutateAsync(parsed.data);
      setInvited({ email: result.email, isPrimary: result.isPrimary });
    } catch (error) {
      setServerError(
        error instanceof ApiClientError
          ? error.message
          : "The administrator could not be created.",
      );
    }
  });

  if (invited) {
    return (
      <div
        role="status"
        className="rounded-lg border border-success bg-success-subtle p-4 text-sm text-success"
      >
        <p>Invitation sent to {invited.email}.</p>
        <p className="mt-1">
          {invited.isPrimary
            ? "They are now the primary administrator of this organization."
            : "They are not the primary administrator, so they cannot yet change other users' roles."}
        </p>
        <Link href={"/platform/organizations/" + organizationId} className="mt-2 inline-block underline">
          Back to organization
        </Link>
      </div>
    );
  }

  return (
    <form onSubmit={submit} aria-label="Create administrator" className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-1">
          <label htmlFor="firstName" className="text-sm font-medium text-foreground">
            First name
          </label>
          <input id="firstName" {...register("firstName")} className={inputClass} />
          {errors.firstName ? (
            <p role="alert" className="text-sm text-destructive">
              {errors.firstName.message}
            </p>
          ) : null}
        </div>
        <div className="space-y-1">
          <label htmlFor="lastName" className="text-sm font-medium text-foreground">
            Last name
          </label>
          <input id="lastName" {...register("lastName")} className={inputClass} />
          {errors.lastName ? (
            <p role="alert" className="text-sm text-destructive">
              {errors.lastName.message}
            </p>
          ) : null}
        </div>
        <div className="space-y-1">
          <label htmlFor="email" className="text-sm font-medium text-foreground">
            Email
          </label>
          <input
            id="email"
            type="email"
            placeholder="admin@example.com"
            {...register("email")}
            className={inputClass}
          />
          {errors.email ? (
            <p role="alert" className="text-sm text-destructive">
              {errors.email.message}
            </p>
          ) : null}
        </div>
        <div className="space-y-1">
          <label htmlFor="mobile" className="text-sm font-medium text-foreground">
            Mobile (optional)
          </label>
          <input id="mobile" {...register("mobile")} className={inputClass} />
          {errors.mobile ? (
            <p role="alert" className="text-sm text-destructive">
              {errors.mobile.message}
            </p>
          ) : null}
        </div>
      </div>

      {/*
        A native checkbox with a real <label htmlFor>, not a styled div: assistive technology and
        keyboard operation already understand it, and the consequence of ticking it is bound to the
        control with aria-describedby rather than left as a floating paragraph.
      */}
      <div className="space-y-1">
        <div className="flex items-start gap-2">
          <input
            id="makePrimary"
            type="checkbox"
            aria-describedby="makePrimary-help"
            {...register("makePrimary")}
            className="mt-0.5 h-4 w-4 rounded border-input"
          />
          <label htmlFor="makePrimary" className="text-sm font-medium text-foreground">
            Make this administrator the primary administrator
          </label>
        </div>
        <p id="makePrimary-help" className="ml-6 text-sm text-muted-foreground">
          The primary administrator is the only one who can change other users&apos; roles.
        </p>
      </div>

      {serverError ? (
        <p role="alert" className="text-sm text-destructive">
          {serverError}
        </p>
      ) : null}

      <div className="flex flex-wrap gap-2">
        <button
          type="submit"
          disabled={isSubmitting || mutation.isPending}
          className="rounded bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:opacity-50"
        >
          {isSubmitting ? "Saving…" : "Create administrator"}
        </button>
        {/*
          A way out of the form. Without it the only escape is the browser back button, which is not
          an affordance the page offers — the user is left looking for one. type="button" so it can
          never submit, and a link back to the administrator list the page was opened from, so a user
          who deep-linked here still lands somewhere useful.
        */}
        <Link
          href={"/platform/organizations/" + organizationId + "/admins"}
          className="inline-flex h-10 items-center justify-center rounded-md border border-input bg-surface px-4 text-sm font-medium text-foreground hover:bg-muted"
        >
          Cancel
        </Link>
      </div>
    </form>
  );
}
