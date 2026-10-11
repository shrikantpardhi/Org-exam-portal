"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import type { ZodType } from "zod";

import { Button } from "@/components/ui/button";
import { ApiClientError } from "@/lib/errors";

export interface MasterFormField {
  name: string;
  label: string;
  type?: string;
}

/**
 * Reusable Academic master-data form ({@code specs/11-P2-Academic-V1.5.md §96, §97, §101}).
 *
 * <p>React Hook Form holds form state; Zod validates on submit; server errors are mapped to a safe
 * message. A hidden button is never authorization — the backend rechecks every permission.
 */
export function MasterForm<T extends Record<string, unknown>>({
  schema,
  fields,
  submitLabel,
  onSubmit,
}: {
  schema: ZodType<T>;
  fields: MasterFormField[];
  submitLabel: string;
  onSubmit: (values: T) => Promise<void>;
}) {
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<Record<string, string>>();
  const [serverError, setServerError] = useState<string | null>(null);

  const submit = handleSubmit(async (raw) => {
    setServerError(null);
    const parsed = schema.safeParse(raw);
    if (!parsed.success) {
      for (const issue of parsed.error.issues) {
        const path = issue.path.join(".");
        if (path) {
          setError(path, { message: issue.message });
        }
      }
      return;
    }
    try {
      await onSubmit(parsed.data);
      reset();
    } catch (error) {
      setServerError(
        error instanceof ApiClientError ? error.message : "The request could not be completed.",
      );
    }
  });

  return (
    <form onSubmit={submit} aria-label={submitLabel} className="space-y-3">
      {fields.map((field) => (
        <div key={field.name} className="space-y-1">
          <label htmlFor={field.name} className="text-sm font-medium">
            {field.label}
          </label>
          <input
            id={field.name}
            type={field.type ?? "text"}
            {...register(field.name)}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
          {errors[field.name] ? (
            <p role="alert" className="text-sm text-red-600">
              {String(errors[field.name]?.message)}
            </p>
          ) : null}
        </div>
      ))}

      {serverError ? (
        <p role="alert" className="text-sm text-red-600">
          {serverError}
        </p>
      ) : null}

      <Button type="submit" disabled={isSubmitting}>
        {isSubmitting ? "Saving…" : submitLabel}
      </Button>
    </form>
  );
}
