import * as React from "react";

import { cn } from "@/lib/utils";

/** Native select. Native is deliberate: keyboard and screen-reader behaviour come for free. */
export const Select = React.forwardRef<HTMLSelectElement, React.SelectHTMLAttributes<HTMLSelectElement>>(
  ({ className, children, ...props }, ref) => (
    <select
      ref={ref}
      className={cn(
        "flex h-10 w-full rounded-md border border-input bg-surface px-3 py-2 text-sm text-foreground shadow-sm transition-colors",
        "disabled:cursor-not-allowed disabled:bg-muted disabled:opacity-60",
        "aria-[invalid=true]:border-destructive aria-[invalid=true]:ring-1 aria-[invalid=true]:ring-destructive",
        className,
      )}
      {...props}
    >
      {children}
    </select>
  ),
);
Select.displayName = "Select";
