"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import type { z, ZodTypeAny } from "zod";

import { ApiClientError } from "@/lib/errors";

/**
 * A compact communication form ({@code specs/19-P10-Communication-V1.5.md §75, §110-§112}).
 *
 * <p>React Hook Form holds local form state and Zod validates before save. Textarea fields carry
 * template or announcement bodies; the form performs no HTML interpretation.
 */
export interface CommunicationFormField {
  name: string;
  label: string;
  type?: "text" | "textarea" | "datetime-local";
  placeholder?: string;
}

export function CommunicationForm<S extends ZodTypeAny>({
  schema,
  fields,
  submitLabel,
  onSubmit,
  defaultValues,
  resetOnSuccess = false,
  disabled = false,
}: {
  schema: S;
  fields: CommunicationFormField[];
  submitLabel: string;
  onSubmit: (values: z.infer<S>) => Promise<void>;
  defaultValues?: Record<string, string>;
  resetOnSuccess?: boolean;
  disabled?: boolean;
}) {
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<Record<string, string>>({ defaultValues });
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
      if (resetOnSuccess) {
        reset();
      }
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
          {field.type === "textarea" ? (
            <textarea
              id={field.name}
              rows={8}
              placeholder={field.placeholder}
              {...register(field.name)}
              className="w-full rounded-md border border-input px-3 py-2 font-mono text-sm"
            />
          ) : (
            <input
              id={field.name}
              type={field.type ?? "text"}
              placeholder={field.placeholder}
              {...register(field.name)}
              className="w-full rounded-md border border-input px-3 py-2 text-sm"
            />
          )}
          {errors[field.name] ? (
            <p role="alert" className="text-sm text-destructive">
              {String(errors[field.name]?.message)}
            </p>
          ) : null}
        </div>
      ))}

      {serverError ? (
        <p role="alert" className="text-sm text-destructive">
          {serverError}
        </p>
      ) : null}

      <button
        type="submit"
        disabled={disabled || isSubmitting}
        className="rounded bg-primary px-4 py-2 text-sm font-medium text-white disabled:opacity-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-900"
      >
        {isSubmitting ? "Saving…" : submitLabel}
      </button>
    </form>
  );
}
