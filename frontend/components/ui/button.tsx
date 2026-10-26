import * as React from "react";
import Link from "next/link";
import { cva, type VariantProps } from "class-variance-authority";

import { cn } from "@/lib/utils";

/**
 * Accessible button primitive (shadcn/ui style).
 *
 * <p>Colours come from the semantic tokens in `globals.css`, so every button in the application
 * shares one palette and one focus treatment (`specs/09-P0-Foundation-V1.5.md` §49).
 */
const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md text-sm font-medium transition-colors disabled:pointer-events-none disabled:opacity-50 [&_svg]:pointer-events-none [&_svg]:shrink-0",
  {
    variants: {
      variant: {
        default: "bg-primary text-primary-foreground hover:bg-primary-hover",
        outline: "border border-input bg-surface text-foreground hover:bg-muted",
        ghost: "text-foreground hover:bg-muted",
        destructive: "bg-destructive text-destructive-foreground hover:bg-destructive/90",
        link: "text-primary underline-offset-4 hover:underline",
      },
      size: {
        sm: "h-9 px-3",
        default: "h-10 px-4 py-2",
        lg: "h-11 px-6",
        icon: "h-10 w-10",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  },
);

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {}

const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, type = "button", ...props }, ref) => (
    <button
      ref={ref}
      type={type}
      className={cn(buttonVariants({ variant, size }), className)}
      {...props}
    />
  ),
);
Button.displayName = "Button";

/**
 * A navigation action that looks like a button.
 *
 * <p>Rendering an anchor through a button component with an `asChild` slot needs Radix, which this
 * app does not depend on. This exports the same visual treatment as a real `next/link`, so a link is
 * a link for assistive technology (focus, context menu, open-in-new-tab) while matching the buttons
 * beside it. Use `Button` for actions, `ButtonLink` for navigation.
 */
const ButtonLink = React.forwardRef<
  HTMLAnchorElement,
  React.AnchorHTMLAttributes<HTMLAnchorElement> & { href: string } & VariantProps<typeof buttonVariants>
>(({ className, variant, size, href, ...props }, ref) => (
  <Link ref={ref} href={href} className={cn(buttonVariants({ variant, size }), className)} {...props} />
));
ButtonLink.displayName = "ButtonLink";

export { Button, ButtonLink, buttonVariants };

