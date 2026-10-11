"use client";

import { useState, type FormEvent } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { forgotPassword } from "@/features/auth/api/auth-api";

/**
 * Forgot password surface ({@code specs/10-P1-Identity-V1.5.md §106, §166}).
 *
 * <p>Always presents the same generic outcome, regardless of whether the account exists. Known
 * and unknown email addresses are indistinguishable.
 */
const GENERIC_OUTCOME = "If the account exists, reset instructions have been sent.";

export function ForgotPasswordForm() {
  const [email, setEmail] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) {
      return;
    }
    setSubmitting(true);
    try {
      await forgotPassword(email.trim());
    } catch {
      // The generic outcome is shown regardless; the request is non-enumerating by design.
    } finally {
      setSubmitting(false);
      setSubmitted(true);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Reset your password</CardTitle>
      </CardHeader>
      <CardContent>
        {submitted ? (
          <p role="status" className="text-sm">
            {GENERIC_OUTCOME}
          </p>
        ) : (
          <form onSubmit={onSubmit} aria-label="Forgot password" className="space-y-4">
            <div className="space-y-1">
              <label htmlFor="forgot-email" className="text-sm font-medium">
                Email
              </label>
              <input
                id="forgot-email"
                name="email"
                type="email"
                autoComplete="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
              />
            </div>
            <Button type="submit" disabled={submitting} className="w-full">
              {submitting ? "Sending…" : "Send reset instructions"}
            </Button>
          </form>
        )}
      </CardContent>
    </Card>
  );
}
