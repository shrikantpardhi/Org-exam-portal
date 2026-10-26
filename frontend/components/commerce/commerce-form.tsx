"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import type { z, ZodTypeAny } from "zod";

import { ApiClientError } from "@/lib/errors";

/**
 * A compact commerce form ({@code specs/18-P9-Commerce-V1.5.md §76}).
 *
 * <p>React Hook Form owns the local form state and Zod validates on submit, matching the platform
 * pattern. The form never computes a price or a status; it only submits the operator's or student's
 * input to the server.
 */
export interface CommerceFormField {
  name: string;
  label: string;
  type?: "text" | "number" | "datetime-local" | "select";
  options?: { value: string; label: string }[];
  step?: string;
  placeholder?: string;
}

export function CommerceForm<S extends ZodTypeAny>({
  schema,
  fields,
  submitLabel,
  onSubmit,
  disabled = false,
}: {
  schema: S;
  fields: CommerceFormField[];
  submitLabel: string;
  onSubmit: (values: z.infer<S>) => Promise<void>;
  disabled?: boolean;
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
          {field.type === "select" ? (
            <select
              id={field.name}
              {...register(field.name)}
              className="w-full rounded-md border border-input px-3 py-2 text-sm"
            >
              <option value="">Select…</option>
              {(field.options ?? []).map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          ) : (
            <input
              id={field.name}
              type={field.type ?? "text"}
              step={field.step}
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
