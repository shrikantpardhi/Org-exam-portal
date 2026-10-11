import { LoginForm } from "@/features/auth/components/login-form";

export default function LoginPage() {
  return (
    <main className="mx-auto flex w-full max-w-md flex-col gap-6 px-6 py-16">
      <h1 className="text-2xl font-semibold tracking-tight">Smart Competitive Examination Platform</h1>
      <LoginForm />
    </main>
  );
}
