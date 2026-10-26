"use client";

import { AuthenticatedGuard } from "@/features/auth/components/authenticated-guard";
import { TemplateListScreen } from "@/features/communication/components/template-list-screen";

/** Email template list ({@code specs/19-P10-Communication-V1.5.md §110}). */
export default function AdminTemplatesPage() {
  return (
    <div className="mx-auto flex w-full max-w-5xl flex-col gap-6 px-6 py-16">
      <header className="space-y-1">
        <p className="text-sm text-muted-foreground">Administration · Communication</p>
        <h1 className="text-2xl font-semibold tracking-tight">Email templates</h1>
      </header>
      <AuthenticatedGuard permission="COMMUNICATION_TEMPLATE_READ">
        <TemplateListScreen />
      </AuthenticatedGuard>
    </div>
  );
}
