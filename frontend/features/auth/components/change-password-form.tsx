"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useAuth } from "@/lib/auth/auth-context";
import { roleHome } from "@/lib/auth/role-home";
import { ApiClientError } from "@/lib/errors";

/**
 * Authenticated password change ({@code specs/05 §72.1}; ADR-0034). Used both for the forced
 * first-login reset and for voluntary rotation from the avatar menu.
 */
export function ChangePasswordForm() {
  const { changePassword, user } = useAuth();
  const router = useRouter();
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;
    if (currentPassword === "" || newPassword === "" || confirmPassword === "") {
      setError("All fields are required.");
      return;
    }
    if (newPassword !== confirmPassword) {
      setError("The new password and confirmation do not match.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await changePassword(currentPassword, newPassword);
      router.push(roleHome(user?.roles ?? [], false));
    } catch (err) {
      if (err instanceof ApiClientError) {
        setError(err.fieldErrors?.[0]?.message ?? err.message);
      } else {
        setError("Something went wrong. Please try again.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Change your password</CardTitle>
        <p className="text-sm text-muted-foreground">
          You must choose a new password before continuing.
        </p>
      </CardHeader>
      <CardContent>
        <form onSubmit={onSubmit} aria-label="Change password" className="space-y-4">
          <div className="space-y-1">
            <label htmlFor="current-password" className="text-sm font-medium">Current password</label>
            <input
              id="current-password"
              type="password"
              autoComplete="current-password"
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              className="w-full rounded-md border border-input px-3 py-2 text-sm"
            />
          </div>
          <div className="space-y-1">
            <label htmlFor="new-password" className="text-sm font-medium">New password</label>
            <input
              id="new-password"
              type="password"
              autoComplete="new-password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              className="w-full rounded-md border border-input px-3 py-2 text-sm"
            />
          </div>
          <div className="space-y-1">
            <label htmlFor="confirm-password" className="text-sm font-medium">Confirm new password</label>
            <input
              id="confirm-password"
              type="password"
              autoComplete="new-password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              className="w-full rounded-md border border-input px-3 py-2 text-sm"
            />
          </div>
          {error ? <p role="alert" className="text-sm text-destructive">{error}</p> : null}
          <Button type="submit" disabled={submitting} className="w-full">
            {submitting ? "Changing…" : "Change password"}
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}
