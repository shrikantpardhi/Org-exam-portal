import * as React from "react";

import { cn } from "@/lib/utils";

/** Multi-line text input. */
export const Textarea = React.forwardRef<
  HTMLTextAreaElement,
  React.TextareaHTMLAttributes<HTMLTextAreaElement>
>(({ className, ...props }, ref) => (
  <textarea
    ref={ref}
    className={cn(
      "flex min-h-[80px] w-full rounded-md border border-input bg-surface px-3 py-2 text-sm text-foreground shadow-sm transition-colors",
      "placeholder:text-muted-foreground",
      "disabled:cursor-not-allowed disabled:bg-muted disabled:opacity-60",
      "aria-[invalid=true]:border-destructive aria-[invalid=true]:ring-1 aria-[invalid=true]:ring-destructive",
      className,
    )}
    {...props}
  />
));
Textarea.displayName = "Textarea";
