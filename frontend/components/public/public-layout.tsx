"use client";

import Link from "next/link";
import { useEffect, useState, type ReactNode } from "react";


const NAV = [
 { href: "/#capabilities", label: "Capabilities" },
 { href: "/#how-it-works", label: "How it works" },
 { href: "/#pricing", label: "Pricing" },
 { href: "/docs", label: "Docs" },
];

/**
 * The public layout ({@code docs/frontend/public-landing.md}, navigation.md): a header that is
 * transparent over the hero and solid once the page scrolls past ~8 px, then the page content,
 * then a footer. No sidebar, no avatar.
 */
export function PublicLayout({ children }: { children: ReactNode }) {
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 8);
 onScroll();
 window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  return (
    <div className="flex min-h-screen flex-col">
      <header
 className={
 scrolled
 ? "sticky top-0 z-40 border-b border-border bg-surface/95 backdrop-blur transition-colors"
 : "sticky top-0 z-40 border-b border-transparent bg-transparent transition-colors"
        }
      >
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-6">
          <Link href="/" className="text-lg font-semibold tracking-tight text-foreground">
            Exam Platform
          </Link>
          <nav className="hidden items-center gap-6 text-sm md:flex">
            {NAV.map((item) => (
              <Link
 key={item.href}
 href={item.href}
 className="text-muted-foreground hover:text-foreground"
              >
                {item.label}
              </Link>
            ))}
          </nav>
          <div className="flex items-center gap-3">
            <Link
 href="/login"
 className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-hover"
            >
              Sign in
            </Link>
          </div>
        </div>
      </header>

      <main className="flex-1">{children}</main>

      <footer className="border-t border-border py-12">
        <div className="mx-auto flex max-w-6xl flex-col gap-8 px-6 md:flex-row md:justify-between">
          <div className="max-w-sm space-y-2">
            <p className="text-lg font-semibold text-foreground">Exam Platform</p>
            <p className="text-sm text-muted-foreground">
              A modular examination system — author, schedule, deliver, and evaluate competitive
              examinations end to end.
            </p>
          </div>
          <div className="flex gap-12 text-sm">
            <div className="space-y-2">
              <p className="font-medium text-foreground">Product</p>
              <p><Link className="text-muted-foreground hover:text-foreground" href="/docs">Docs</Link></p>
              <p><Link className="text-muted-foreground hover:text-foreground" href="/#capabilities">Capabilities</Link></p>
            </div>
            <div className="space-y-2">
              <p className="font-medium text-foreground">Account</p>
              <p><Link className="text-muted-foreground hover:text-foreground" href="/login">Sign in</Link></p>
              <p><Link className="text-muted-foreground hover:text-foreground" href="/#contact">Contact</Link></p>
            </div>
            <div className="space-y-2">
              <p className="font-medium text-foreground">Legal</p>
              {/* `/docs/terms`, `/docs/privacy` and `/docs/support` are not bundled pages: only the
                  `/docs` index and its `[slug]` articles exist, so the three links pointed at 404s.
                  They now all resolve to the docs index rather than to routes that do not exist. */}
              <p><Link className="text-muted-foreground hover:text-foreground" href="/docs">Terms</Link></p>
              <p><Link className="text-muted-foreground hover:text-foreground" href="/docs">Privacy</Link></p>
              <p><Link className="text-muted-foreground hover:text-foreground" href="/docs">Support</Link></p>
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
}
