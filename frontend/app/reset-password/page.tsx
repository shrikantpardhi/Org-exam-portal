"use client";

import { Suspense } from "react";
import { useSearchParams } from "next/navigation";

import { ResetPasswordForm } from "@/features/auth/components/reset-password-form";

function ResetPasswordWithToken() {
  const searchParams = useSearchParams();
  const token = searchParams.get("token") ?? "";
  return <ResetPasswordForm initialToken={token} />;
}

export default function ResetPasswordPage() {
  return (
    <main className="mx-auto flex w-full max-w-md flex-col gap-6 px-6 py-16">
      <h1 className="text-2xl font-semibold tracking-tight">Choose a new password</h1>
      <Suspense fallback={<p className="text-sm text-muted-foreground">Loading…</p>}>
        <ResetPasswordWithToken />
      </Suspense>
    </main>
  );
}
