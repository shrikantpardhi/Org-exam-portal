"use client";

import { useState, type FormEvent } from "react";

import { Button } from "@/components/ui/button";
import { submitContact } from "@/features/public/api/contact-api";
import { ApiClientError } from "@/lib/errors";

type State =
  | { phase: "idle" }
  | { phase: "submitting" }
  | { phase: "success" }
  | { phase: "error"; message: string };

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/**
 * Public landing-page contact form ({@code docs/frontend/public-landing.md} §9). Validates
 * name/email/message client-side and server-side, surfaces the per-IP rate-limit message, and
 * never creates a user.
 */
export function ContactForm() {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [organization, setOrganization] = useState("");
  const [message, setMessage] = useState("");
  const [state, setState] = useState<State>({ phase: "idle" });

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (state.phase === "submitting") return;

    if (name.trim() === "" || email.trim() === "" || message.trim() === "") {
      setState({ phase: "error", message: "Name, email, and message are required." });
      return;
    }
    if (!EMAIL_PATTERN.test(email.trim())) {
      setState({ phase: "error", message: "Enter a valid email address." });
      return;
    }

    setState({ phase: "submitting" });
    try {
      await submitContact({
        name: name.trim(),
        email: email.trim(),
        message: message.trim(),
        ...(organization.trim() ? { organization: organization.trim() } : {}),
      });
      setState({ phase: "success" });
    } catch (err) {
      if (err instanceof ApiClientError && err.status === 429) {
        setState({ phase: "error", message: "Too many requests — please try again shortly." });
      } else if (err instanceof ApiClientError) {
        setState({ phase: "error", message: err.message });
      } else {
        setState({ phase: "error", message: "Something went wrong. Please try again." });
      }
    }
  }

  if (state.phase === "success") {
    return (
      <p role="status" className="rounded-md border border-green-200 bg-success-subtle p-4 text-sm text-success">
        Thanks — we&apos;ll be in touch.
      </p>
    );
  }

  return (
    <form onSubmit={onSubmit} aria-label="Contact" noValidate className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-1">
          <label htmlFor="contact-name" className="text-sm font-medium">Name</label>
          <input
            id="contact-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="w-full rounded-md border border-input px-3 py-2 text-sm"
          />
        </div>
        <div className="space-y-1">
          <label htmlFor="contact-email" className="text-sm font-medium">Email</label>
          <input
            id="contact-email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="w-full rounded-md border border-input px-3 py-2 text-sm"
          />
        </div>
      </div>
      <div className="space-y-1">
        <label htmlFor="contact-organization" className="text-sm font-medium">Organization (optional)</label>
        <input
          id="contact-organization"
          value={organization}
          onChange={(e) => setOrganization(e.target.value)}
          className="w-full rounded-md border border-input px-3 py-2 text-sm"
        />
      </div>
      <div className="space-y-1">
        <label htmlFor="contact-message" className="text-sm font-medium">Message</label>
        <textarea
          id="contact-message"
          rows={4}
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          className="w-full rounded-md border border-input px-3 py-2 text-sm"
        />
      </div>
      {state.phase === "error" ? (
        <p role="alert" className="text-sm text-destructive">{state.message}</p>
      ) : null}
      <Button type="submit" disabled={state.phase === "submitting"}>
        {state.phase === "submitting" ? "Sending…" : "Send message"}
      </Button>
    </form>
  );
}
