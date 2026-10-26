import { apiPost } from "@/lib/api-client";

export interface ContactPayload {
  name: string;
  email: string;
  message: string;
  organization?: string;
}

/** Submits the public landing-page contact form (ADR-0037). */
export async function submitContact(payload: ContactPayload): Promise<void> {
  await apiPost("/public/contact", payload);
}
