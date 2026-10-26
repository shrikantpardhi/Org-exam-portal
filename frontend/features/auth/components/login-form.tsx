"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState, type FormEvent } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useAuth } from "@/lib/auth/auth-context";
import { roleHome, safeNext } from "@/lib/auth/role-home";
import { ApiClientError } from "@/lib/errors";
import type { LoginPortal } from "@/schemas/auth";

const PORTAL_LABELS: Record<LoginPortal, string> = {
  SYSTEM_ADMIN: "System admin",
  ORGANIZATION_ADMIN: "Organization admin",
  STUDENT: "Student",
};

const PORTAL_ROUTES: Record<LoginPortal, string> = {
  SYSTEM_ADMIN: "/login/admin",
  ORGANIZATION_ADMIN: "/login/organization",
  STUDENT: "/login/student",
};

/**
 * Split-login surface ({@code specs/10-P1-Identity-V1.5.md §104}; ADR-0035).
 *
 * <p>A portal-aware form: it sends the chosen {@code portal}, surfaces the generic
 * {@code PORTAL_MISMATCH} message without naming the correct portal, and redirects to the
 * role home (or a safe {@code next} target, or {@code /change-password} on a forced reset).
 */
export function LoginForm({
  portal,
  title = "Sign in",
  subtitle,
}: {
  portal?: LoginPortal;
  title?: string;
  subtitle?: string;
}) {
  const { login } = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) {
      return;
    }
    if (username.trim() === "" || password === "") {
      setError("Enter your username and password.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const result = await login(username.trim(), password, portal);
      const destination =
        safeNext(searchParams.get("next")) ?? roleHome(result.user.roles, result.user.mustChangePassword);
      router.push(destination);
    } catch (err) {
      if (err instanceof ApiClientError && err.code === "PORTAL_MISMATCH") {
        setError("This account cannot use this portal.");
      } else {
        setError("Invalid username or password.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        {subtitle ? <p className="text-sm text-muted-foreground">{subtitle}</p> : null}
      </CardHeader>
      <CardContent>
        <form onSubmit={onSubmit} aria-label="Sign in" className="space-y-4">
          <div className="space-y-1">
            <label htmlFor="username" className="text-sm font-medium">
              Email
            </label>
            <input
              id="username"
              name="username"
              type="email"
              autoComplete="username"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              className="w-full rounded-md border border-input px-3 py-2 text-sm"
            />
          </div>

          <div className="space-y-1">
            <label htmlFor="password" className="text-sm font-medium">
              Password
            </label>
            <input
              id="password"
              name="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              className="w-full rounded-md border border-input px-3 py-2 text-sm"
            />
          </div>

          {error ? (
            <p role="alert" className="text-sm text-red-600">
              {error}
            </p>
          ) : null}

          <Button type="submit" disabled={submitting} className="w-full">
            {submitting ? "Signing in…" : "Sign in"}
          </Button>
        </form>

        <p className="mt-4 text-sm">
          <Link href="/forgot-password" className="text-foreground underline">
            Forgot your password?
          </Link>
        </p>

        {portal ? (
          <p className="mt-2 text-xs text-muted-foreground">
            Wrong portal?{" "}
            {portalAlternatives(portal)}
          </p>
        ) : null}
      </CardContent>
    </Card>
  );
}

function portalAlternatives(current: LoginPortal) {
  const others = (Object.keys(PORTAL_ROUTES) as LoginPortal[]).filter((portal) => portal !== current);
  return others.map((portal, index) => (
    <span key={portal}>
      {index > 0 ? " · " : ""}
      <Link href={PORTAL_ROUTES[portal]} className="underline">
        {PORTAL_LABELS[portal]}
      </Link>
    </span>
  ));
}
