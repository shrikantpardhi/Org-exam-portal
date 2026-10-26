import * as React from "react";

import { cn } from "@/lib/utils";

/**
 * Text input primitive.
 *
 * <p>`aria-invalid` is styled, not just announced: a field that fails validation must look wrong as
 * well as read wrong, and colour is never the only signal (WCAG 1.4.1).
 */
export const Input = React.forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement>>(
  ({ className, type = "text", ...props }, ref) => (
    <input
      ref={ref}
      type={type}
      className={cn(
        "flex h-10 w-full rounded-md border border-input bg-surface px-3 py-2 text-sm text-foreground shadow-sm transition-colors",
        "placeholder:text-muted-foreground",
        "disabled:cursor-not-allowed disabled:bg-muted disabled:opacity-60",
        "aria-[invalid=true]:border-destructive aria-[invalid=true]:ring-1 aria-[invalid=true]:ring-destructive",
        className,
      )}
      {...props}
    />
  ),
);
Input.displayName = "Input";
