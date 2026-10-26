"use client";

import { useRouter } from "next/navigation";

import { AdminForm, type AdminFormField } from "@/components/admin/admin-form";
import { useCreateOrganization } from "@/features/platform/hooks/use-platform";
import { createOrganizationSchema, type CreateOrganization } from "@/features/platform/schemas/platform";

/** Create a business organization (super-admin-flow §6). */
export function OrganizationForm() {
  const router = useRouter();
  const mutation = useCreateOrganization();

  const fields: AdminFormField[] = [
    { name: "code", label: "Code", placeholder: "acme" },
    { name: "name", label: "Name", placeholder: "Acme University" },
    { name: "description", label: "Description", type: "textarea", placeholder: "Optional" },
  ];

  return (
    <AdminForm
      schema={createOrganizationSchema}
      fields={fields}
      submitLabel="Create organization"
      onSubmit={async (values) => {
        const created = await mutation.mutateAsync(values as CreateOrganization);
        // Land on the detail page with an explicit handover cue: creating an organization is not
        // finished until its single administrator exists, so the next step is stated, not implied.
        router.push("/platform/organizations/" + created.id + "?created=1");
      }}
    />
  );
}
