"use client";

import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";

import { describeAdminError } from "@/components/admin/format";
import { Button, ButtonLink } from "@/components/ui/button";
import {
  EMPTY_ORGANIZATION_PROFILE,
  OrganizationProfileFields,
  createOrganizationPayload,
} from "@/features/platform/components/organization-profile-fields";
import { useCreateOrganization } from "@/features/platform/hooks/use-platform";
import { createOrganizationSchema } from "@/features/platform/schemas/platform";

/**
 * Create a business organization (super-admin-flow §6; V37, ADR-0056).
 *
 * <p>Collects the whole profile at creation, because the operator registering an institute already
 * knows its address and contact point. Built on React Hook Form directly rather than on the shared
 * {@code AdminForm}: that component renders one flat list of inputs, and thirteen of them in a
 * single column hide the fact that only two are required.
 *
 * <p>The three groups are the ones the operator thinks in — who the organization is, where it is,
 * how to reach it — and the address and contact sections are marked optional once, at section
 * level, instead of stamping "optional" on ten individual labels.
 */
export function OrganizationForm() {
  const router = useRouter();
  const mutation = useCreateOrganization();

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<Record<string, string>>({
    defaultValues: { ...EMPTY_ORGANIZATION_PROFILE, code: "" },
  });

  const submit = handleSubmit(async (raw) => {
    const parsed = createOrganizationSchema.safeParse(raw);
    if (!parsed.success) {
      for (const issue of parsed.error.issues) {
        const path = issue.path[0];
        if (typeof path === "string") {
          setError(path, { message: issue.message });
        }
      }
      return;
    }
    try {
      const created = await mutation.mutateAsync(createOrganizationPayload(parsed.data));
      // Land on the detail page with an explicit handover cue: creating an organization is not
      // finished until its single administrator exists, so the next step is stated, not implied.
      router.push("/platform/organizations/" + created.id + "?created=1");
    } catch {
      // Rendered from mutation.isError below, so the operator sees why creation failed rather than
      // watching the button return to rest with nothing said.
    }
  });

  return (
    <form onSubmit={submit} aria-label="Create organization" className="space-y-6" noValidate>
      <OrganizationProfileFields register={register} errors={errors} includeCode />

      {mutation.isError ? (
        <p role="alert" className="text-sm text-destructive">
          {describeAdminError(mutation.error, "The organization could not be created.")}
        </p>
      ) : null}

      <div className="flex flex-wrap items-center gap-2">
        <Button type="submit" disabled={isSubmitting || mutation.isPending}>
          {isSubmitting || mutation.isPending ? "Creating…" : "Create organization"}
        </Button>
        {/*
          Cancel goes back to the list this form was opened from, not to browser history: an
          operator who deep-linked straight to /new would otherwise be left with nowhere useful to
          land. It is a link, so it can never submit the form it sits in, and it stays enabled
          while the create is in flight — an escape hatch that stops working exactly when the
          operator wants it would be worse than the double-action it is meant to prevent.
        */}
        <ButtonLink variant="outline" href="/platform/organizations">
          Cancel
        </ButtonLink>
      </div>
    </form>
  );
}
