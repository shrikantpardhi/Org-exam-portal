import { Suspense } from "react";

import { PublicLayout } from "@/components/public/public-layout";
import { LoginForm } from "@/features/auth/components/login-form";

/** ADMIN split-login portal ({@code docs/frontend/login-portals.md}). */
export default function OrganizationLoginPage() {
  return (
    <PublicLayout>
      <div className="flex min-h-[calc(100vh-4rem)] items-center justify-center px-6 py-16">
        <div className="w-full max-w-sm">
          <Suspense fallback={null}>
            <LoginForm
              portal="ORGANIZATION_ADMIN"
              title="Organization admin sign in"
              subtitle="Manage your organization's users, content, schedules, and configuration."
            />
          </Suspense>
        </div>
      </div>
    </PublicLayout>
  );
}
