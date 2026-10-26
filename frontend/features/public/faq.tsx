"use client";

import { useState } from "react";

const ITEMS = [
 {
 q: "Who runs the platform for us?",
 a: "You do. Your SUPER_ADMIN creates the organization and its ADMIN; the ADMIN manages users and content.",
 },
 {
 q: "Does it support math and science questions?",
 a: "Yes; the question bank renders LaTeX.",
 },
 {
 q: "Which exam patterns are supported?",
 a: "NTA, TCS iON, and IBPS-style.",
 },
 {
 q: "Can we take payments?",
 a: "Reference adapters are active in V1.5; real provider adapters are not yet implemented.",
 },
 {
 q: "How do we get started?",
 a: "Contact us for a trial; we set up your organization and first admin.",
  },
];

/** One-open-at-a-time FAQ accordion ({@code docs/frontend/public-landing.md} §8). */
export function Faq() {
  const [open, setOpen] = useState<number | null>(null);

  return (
    <div className="divide-y divide-border">
      {ITEMS.map((item, index) => {
        const expanded = open === index;
        return (
          <div key={item.q}>
            <button
 type="button"
              onClick={() => setOpen(expanded ? null : index)}
 aria-expanded={expanded}
 className="flex w-full items-center justify-between py-4 text-left text-base font-medium text-foreground"
            >
              {item.q}
              <span aria-hidden className="text-muted-foreground">{expanded ? "−" : "+"}</span>
            </button>
            {expanded ? <p className="pb-4 text-sm text-muted-foreground">{item.a}</p> : null}
          </div>
        );
      })}
    </div>
  );
}
