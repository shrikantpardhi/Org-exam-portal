import { ChangePasswordForm } from "@/features/auth/components/change-password-form";

/** Forced first-login password change ({@code specs/05 §72.1}). The backend rejects other calls. */
export default function ChangePasswordPage() {
  return <ChangePasswordForm />;
}
