"use client";

import type { FieldErrors, UseFormRegister } from "react-hook-form";

import {
  ORGANIZATION_PROFILE_FIELDS,
  type CreateOrganization,
  type OrganizationDetail,
} from "@/features/platform/schemas/platform";

const inputClass = "w-full rounded-md border border-input bg-surface px-3 py-2 text-sm";

/** One labelled control in a profile fieldset. */
interface ProfileField {
  name: string;
  label: string;
  type?: "text" | "email" | "tel" | "url" | "textarea";
  placeholder?: string;
  /** Full-width in the two-column grid (the textarea). */
  wide?: boolean;
}

/**
 * The identity fields every organization form edits.
 *
 * <p>{@code code} is deliberately not part of this list: it is the organization's stable handle and
 * the correction endpoint refuses it, so only the create form renders it.
 */
const IDENTITY_FIELDS: ProfileField[] = [
  { name: "name", label: "Name (required)", placeholder: "Acme University" },
  {
    name: "description",
    label: "Description",
    type: "textarea",
    placeholder: "Optional",
    wide: true,
  },
];

/** The address columns (V37, ADR-0051). All optional: an organization may have no public address. */
const ADDRESS_FIELDS: ProfileField[] = [
  { name: "addressLine1", label: "Address line 1", placeholder: "12 Example Street" },
  { name: "addressLine2", label: "Address line 2", placeholder: "Optional" },
  { name: "city", label: "City" },
  { name: "stateRegion", label: "State or region" },
  { name: "postalCode", label: "Postal code" },
  { name: "country", label: "Country" },
];

/** The contact columns (V37). Shown on the public organization profile, so they are contact data. */
const CONTACT_FIELDS: ProfileField[] = [
  { name: "contactEmail", label: "Contact email", type: "email", placeholder: "office@example.edu" },
  { name: "contactPhone", label: "Contact phone", type: "tel", placeholder: "+91 80 0000 0000" },
  { name: "websiteUrl", label: "Website", type: "url", placeholder: "https://example.edu" },
  { name: "logoUrl", label: "Logo URL", type: "url", placeholder: "https://example.edu/logo.png" },
];

/**
 * An empty profile form state.
 *
 * <p>Every value is a string rather than {@code null} because an input cannot hold null; "" is the
 * "not set" state throughout the form and is translated to "omitted" or "cleared" at submit time.
 */
export const EMPTY_ORGANIZATION_PROFILE: Record<string, string> = {
  name: "",
  description: "",
  ...Object.fromEntries(ORGANIZATION_PROFILE_FIELDS.map((field) => [field, ""])),
};

/**
 * Seeds the edit form from the organization the server returned.
 *
 * <p>Nulls collapse to "" so an unfilled column is an empty input rather than the text "null".
 */
export function organizationProfileValues(organization: OrganizationDetail): Record<string, string> {
  const values: Record<string, string> = {
    name: organization.name ?? "",
    description: organization.description ?? "",
  };
  for (const field of ORGANIZATION_PROFILE_FIELDS) {
    values[field] = organization[field] ?? "";
  }
  return values;
}

/**
 * Builds the create body from validated form state.
 *
 * <p>On create an empty string and an absent field mean the same thing ("not provided"), so blank
 * values are dropped rather than sent: the request then states exactly what the operator supplied
 * instead of shipping ten empty strings for the backend to discard.
 */
export function createOrganizationPayload(
  values: CreateOrganization,
): CreateOrganization {
  const payload: CreateOrganization = { code: values.code, name: values.name };
  if (values.description) {
    payload.description = values.description;
  }
  for (const field of ORGANIZATION_PROFILE_FIELDS) {
    const value = values[field];
    if (value) {
      payload[field] = value;
    }
  }
  return payload;
}

/**
 * The fields an edit actually changed, which is what a PATCH has to carry.
 *
 * <p>An omitted field is left unchanged by the server, so sending the whole form would turn every
 * untouched input into a write. Sending only the difference keeps the request honest, and it is the
 * only way an emptied input can mean "clear this field" while an untouched one means "leave it".
 * An empty string is retained, because clearing a value is a change.
 */
export function changedProfileValues(
  current: Record<string, string>,
  next: Record<string, string>,
): Record<string, string> {
  const changed: Record<string, string> = {};
  for (const [key, value] of Object.entries(next)) {
    const before = (current[key] ?? "").trim();
    const after = value.trim();
    if (after !== before) {
      changed[key] = after;
    }
  }
  return changed;
}

function ProfileFieldControl({
  field,
  register,
  errors,
}: {
  field: ProfileField;
  register: UseFormRegister<Record<string, string>>;
  errors: FieldErrors<Record<string, string>>;
}) {
  const error = errors[field.name];
  const className = field.wide ? inputClass + " sm:col-span-2" : inputClass;
  return (
    <div className={field.wide ? "space-y-1 sm:col-span-2" : "space-y-1"}>
      <label htmlFor={field.name} className="text-sm font-medium text-foreground">
        {field.label}
      </label>
      {field.type === "textarea" ? (
        <textarea
          id={field.name}
          rows={3}
          placeholder={field.placeholder}
          {...register(field.name)}
          className={className}
        />
      ) : (
        <input
          id={field.name}
          type={field.type ?? "text"}
          placeholder={field.placeholder}
          {...register(field.name)}
          className={className}
        />
      )}
      {error ? (
        <p role="alert" className="text-sm text-destructive">
          {String(error.message)}
        </p>
      ) : null}
    </div>
  );
}

/**
 * The grouped organization profile fields, shared by the create form and the edit-profile dialog
 * (ADR-0056).
 *
 * <p>Grouped rather than flat because thirteen inputs in one column are unusable: the operator
 * registers an institute, so the sections follow what they know (who it is, where it is, how to
 * reach it). The field list lives here once so the two forms can never drift apart, and so a new
 * V37 column is added in exactly one place.
 *
 * <p>{@code register} and {@code errors} come from the caller's React Hook Form instance: the
 * create form adds {@code code} to the same form state, and this component stays a controlled
 * presentation of the shared subset.
 */
export function OrganizationProfileFields({
  register,
  errors,
  includeCode = false,
}: {
  register: UseFormRegister<Record<string, string>>;
  errors: FieldErrors<Record<string, string>>;
  /** The create form also collects the organization code; the correction endpoint refuses it. */
  includeCode?: boolean;
}) {
  const identityFields: ProfileField[] = includeCode
    ? [{ name: "code", label: "Code (required)", placeholder: "acme" }, ...IDENTITY_FIELDS]
    : IDENTITY_FIELDS;

  return (
    <div className="space-y-6">
      <fieldset className="space-y-3">
        <legend className="text-sm font-semibold text-foreground">Identity</legend>
        <div className="grid gap-4 sm:grid-cols-2">
          {identityFields.map((field) => (
            <ProfileFieldControl key={field.name} field={field} register={register} errors={errors} />
          ))}
        </div>
      </fieldset>

      <fieldset className="space-y-3 border-t border-border pt-4">
        <legend className="text-sm font-semibold text-foreground">Address</legend>
        <p className="text-xs text-muted-foreground">
          Optional. Shown on this organization&apos;s profile; leave a field empty if it is not known
          or not public.
        </p>
        <div className="grid gap-4 sm:grid-cols-2">
          {ADDRESS_FIELDS.map((field) => (
            <ProfileFieldControl key={field.name} field={field} register={register} errors={errors} />
          ))}
        </div>
      </fieldset>

      <fieldset className="space-y-3 border-t border-border pt-4">
        <legend className="text-sm font-semibold text-foreground">Contact</legend>
        <p className="text-xs text-muted-foreground">
          Optional. Used for support and for the organization&apos;s public contact point.
        </p>
        <div className="grid gap-4 sm:grid-cols-2">
          {CONTACT_FIELDS.map((field) => (
            <ProfileFieldControl key={field.name} field={field} register={register} errors={errors} />
          ))}
        </div>
      </fieldset>
    </div>
  );
}
