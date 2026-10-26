import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";

import { cn } from "@/lib/utils";

/**
 * Status badge.
 *
 * <p>Every variant carries a text label, never colour alone — a red pill with no word in it is
 * invisible to a user who cannot distinguish the hue (WCAG 1.4.1).
 */
const badgeVariants = cva(
  "inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs font-medium",
  {
    variants: {
      variant: {
        neutral: "border-border bg-muted text-muted-foreground",
        success: "border-success/20 bg-success-subtle text-success",
        warning: "border-warning/20 bg-warning-subtle text-warning",
        danger: "border-destructive/20 bg-destructive-subtle text-destructive",
        info: "border-info/20 bg-info-subtle text-info",
        primary: "border-primary/20 bg-primary-subtle text-primary",
      },
    },
    defaultVariants: { variant: "neutral" },
  },
);

export interface BadgeProps
  extends React.HTMLAttributes<HTMLSpanElement>,
    VariantProps<typeof badgeVariants> {}

export function Badge({ className, variant, ...props }: BadgeProps) {
  return <span className={cn(badgeVariants({ variant }), className)} {...props} />;
}
