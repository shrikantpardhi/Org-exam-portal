import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";

import { cn } from "@/lib/utils";

/**
 * Inline message.
 *
 * <p>`tone="danger"` renders `role="alert"` so a failure interrupts a screen reader; the other tones
 * render `role="status"` so a success does not. That mapping is the reason this is a component: it was
 * previously decided per call site, and inconsistently.
 */
const alertVariants = cva("flex gap-3 rounded-md border p-3 text-sm", {
  variants: {
    tone: {
      info: "border-info/20 bg-info-subtle text-foreground",
      success: "border-success/20 bg-success-subtle text-foreground",
      warning: "border-warning/20 bg-warning-subtle text-foreground",
      danger: "border-destructive/20 bg-destructive-subtle text-foreground",
      neutral: "border-border bg-muted text-foreground",
    },
  },
  defaultVariants: { tone: "info" },
});

export interface AlertProps
  extends Omit<React.HTMLAttributes<HTMLDivElement>, "title">,
    VariantProps<typeof alertVariants> {
  title?: React.ReactNode;
}

export function Alert({ className, tone = "info", title, children, ...props }: AlertProps) {
  return (
    <div
      role={tone === "danger" ? "alert" : "status"}
      className={cn(alertVariants({ tone }), className)}
      {...props}
    >
      <div className="flex flex-col gap-1">
        {title ? <p className="font-medium">{title}</p> : null}
        {children ? <div className="text-muted-foreground">{children}</div> : null}
      </div>
    </div>
  );
}
