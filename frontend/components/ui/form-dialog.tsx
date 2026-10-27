"use client";

import { useEffect, useId, useRef, type FormEvent, type ReactNode } from "react";

import { cn } from "@/lib/utils";

/** The control a dialog puts its initial focus on: the first field, else the submit button. */
const FOCUSABLE_SELECTOR = "input:not([disabled]), select:not([disabled]), textarea:not([disabled])";

/**
 * A modal dialog that hosts a form ({@code specs/20-P11-Administration-V1.5.md §68, §129}).
 *
 * <p>The contract is deliberately the same as {@link import("@/components/admin/confirm-dialog")}'s:
 * {@code role="dialog"}, {@code aria-modal="true"}, a labelled heading, Escape cancels, focus starts
 * inside the dialog, and a dimmed backdrop intercepts the pointer. The differences are the ones a
 * form needs — it renders caller-supplied fields, it owns a real {@code <form>} so Enter submits, and
 * its footer pairs a submit button with Cancel.
 *
 * <p>It is a component rather than a copy of the markup per screen because a modal that is not
 * labelled, or that Escape does not close, is an accessibility regression that would otherwise have
 * to be re-proved at every call site. Editing happens here rather than inside a table cell on
 * purpose: an input rendered by a TanStack cell is recreated on every keystroke, which drops focus.
 */
export interface FormDialogProps {
  open: boolean;
  title: string;
  description?: string;
  /** Defaults to "Save". */
  submitLabel?: string;
  /** Disables Cancel and submit and relabels the submit button while a request is in flight. */
  busy?: boolean;
  onCancel: () => void;
  onSubmit: () => void;
  /** The form fields. */
  children: ReactNode;
  className?: string;
}

export function FormDialog({
  open,
  title,
  description,
  submitLabel = "Save",
  busy = false,
  onCancel,
  onSubmit,
  children,
  className,
}: FormDialogProps) {
  const titleId = useId();
  const descriptionId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
  const submitRef = useRef<HTMLButtonElement>(null);

  // Initial focus goes to the first field, not the submit button: a form dialog exists to be filled
  // in, and landing on submit invites an accidental Enter that saves the untouched defaults. With no
  // fields (a zero-field form) the submit button is the sensible landing place.
  useEffect(() => {
    if (!open) {
      return;
    }
    const dialog = dialogRef.current;
    const field = dialog?.querySelector<HTMLElement>(FOCUSABLE_SELECTOR);
    (field ?? submitRef.current)?.focus();
  }, [open]);

  useEffect(() => {
    if (!open) {
      return;
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        onCancel();
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [open, onCancel]);

  if (!open) {
    return null;
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onSubmit();
  }

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-primary/40 p-4" role="presentation">
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={description ? descriptionId : undefined}
        className={cn(
          "w-full max-w-lg space-y-4 rounded-lg border border-border bg-surface p-6 shadow-xl",
          className,
        )}
      >
        <h2 id={titleId} className="text-lg font-semibold text-foreground">
          {title}
        </h2>
        {description ? (
          <p id={descriptionId} className="text-sm text-muted-foreground">
            {description}
          </p>
        ) : null}

        <form className="space-y-4" onSubmit={handleSubmit} noValidate>
          {children}
          <div className="flex flex-wrap justify-end gap-2">
            <button
              type="button"
              onClick={onCancel}
              disabled={busy}
              className="rounded-md border border-input bg-surface px-4 py-2 text-sm font-medium text-foreground transition-colors hover:bg-muted disabled:pointer-events-none disabled:opacity-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
            >
              Cancel
            </button>
            <button
              ref={submitRef}
              type="submit"
              disabled={busy}
              className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary-hover disabled:pointer-events-none disabled:opacity-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
            >
              {busy ? "Working…" : submitLabel}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
