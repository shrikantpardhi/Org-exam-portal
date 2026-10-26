import { Suspense } from "react";

import { PublicLayout } from "@/components/public/public-layout";
import { LoginForm } from "@/features/auth/components/login-form";

/** Student split-login portal ({@code docs/frontend/login-portals.md}). */
export default function StudentLoginPage() {
  return (
    <PublicLayout>
      <div className="flex min-h-[calc(100vh-4rem)] items-center justify-center px-6 py-16">
        <div className="w-full max-w-sm">
          <Suspense fallback={null}>
            <LoginForm
              portal="STUDENT"
              title="Student sign in"
              subtitle="Attempt exams and view your results, scorecards, and reports."
            />
          </Suspense>
        </div>
      </div>
    </PublicLayout>
  );
}
