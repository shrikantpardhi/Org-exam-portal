import { redirect } from "next/navigation";

/** Legacy /login entry point now redirects to the student portal ({@code docs/frontend/login-portals.md}). */
export default function LoginIndexPage() {
  redirect("/login/student");
}
