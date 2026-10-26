"use client";

import Link from "next/link";
import { useState } from "react";

import { AdminForm, type AdminFormField } from "@/components/admin/admin-form";
import { useCreateOrganizationAdmin } from "@/features/platform/hooks/use-platform";
import { createAdminSchema, type CreateAdmin } from "@/features/platform/schemas/platform";

/** Create the organization ADMIN (super-admin-flow §9). */
export function CreateAdminForm({ organizationId }: { organizationId: string }) {
  const mutation = useCreateOrganizationAdmin(organizationId);
  const [invited, setInvited] = useState<string | null>(null);

  const fields: AdminFormField[] = [
    { name: "firstName", label: "First name" },
    { name: "lastName", label: "Last name" },
    { name: "email", label: "Email", placeholder: "admin@example.com" },
    { name: "mobile", label: "Mobile (optional)" },
  ];

  if (invited) {
    return (
      <div
        role="status"
        className="rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-700"
      >
        Invitation sent to {invited}.
        <Link href={"/platform/organizations/" + organizationId} className="ml-3 underline">
          Back to organization
        </Link>
      </div>
    );
  }

  return (
    <AdminForm
      schema={createAdminSchema}
      fields={fields}
      submitLabel="Create administrator"
      onSubmit={async (values) => {
        const result = await mutation.mutateAsync(values as CreateAdmin);
        setInvited(result.email);
      }}
    />
  );
}
