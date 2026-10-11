import { SystemInfoCard } from "@/features/system/system-info-card";

export default function HomePage() {
  return (
    <main className="mx-auto flex w-full max-w-3xl flex-col gap-8 px-6 py-16">
      <header className="space-y-2">
        <h1 className="text-2xl font-semibold tracking-tight">
          Smart Competitive Examination Platform
        </h1>
        <p className="text-sm text-slate-600">
          Foundation build (P0). No examination features are implemented yet; this page verifies
          that the frontend, API client, and backend are connected.
        </p>
      </header>

      <SystemInfoCard />
    </main>
  );
}
