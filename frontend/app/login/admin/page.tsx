import { Suspense } from "react";

import { PublicLayout } from "@/components/public/public-layout";
import { LoginForm } from "@/features/auth/components/login-form";

/** SUPER_ADMIN split-login portal ({@code docs/frontend/login-portals.md}). */
export default function SystemAdminLoginPage() {
  return (
    <PublicLayout>
      <div className="flex min-h-[calc(100vh-4rem)] items-center justify-center px-6 py-16">
        <div className="w-full max-w-sm">
          <Suspense fallback={null}>
            <LoginForm
              portal="SYSTEM_ADMIN"
              title="System admin sign in"
              subtitle="Manage organizations, providers, and the platform."
            />
          </Suspense>
        </div>
      </div>
    </PublicLayout>
  );
}
