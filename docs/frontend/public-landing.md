# Public Landing Page — Design

Route `/`, rendered in the public layout (header + no sidebar). This is a real marketing
page — real copy drawn from the platform's actual capabilities, never "lorem ipsum", and no
checkout in V1.5.

## Layout

`components/public/PublicLayout.tsx`: a header that is **transparent over the hero** and
**solid on scroll** (a thin elevation shadow appears once the page scrolls past ~8 px), then
the sections below, then a footer.

Header (left → right): logo · nav (Home, Capabilities, Pricing, Docs) · theme toggle ·
"Sign in" button. Footer: logo + one-line blurb + link columns to `/docs`, `/docs/terms`,
`/docs/privacy`, `/docs/support`, and `/login`.

## Sections

### 1. Hero

- **Purpose:** state what the platform is and who it is for in one screen.
- **Copy sketch**
  - Headline: "Run secure, large-scale examinations — end to end."
  - Subheadline: "Author, schedule, deliver, and evaluate competitive examinations with a
    configurable engine, LaTeX question bank, analytics, commerce, and reporting."
  - Primary CTA: **Request a demo** → scrolls to the Contact form (anchor link).
  - Secondary CTA: **Explore the platform** → scrolls to Capabilities.
- **Layout:** full-width, centred text over a subtle brand gradient; two buttons side by side.
- **Responsive:** buttons stack vertically below 640 px; headline scales from 40 px (desktop)
  to 28 px (mobile).

### 2. What we do

- **Purpose:** one plain-language paragraph.
- **Copy sketch:** "The platform is a modular examination system that takes an institute from
  question authoring to published results. It provides a configurable exam engine, a question
  bank with LaTeX rendering, three examination patterns (NTA, TCS iON, and IBPS-style),
  analytics, commerce, communication, and reporting — delivered through role-based portals for
  administrators, staff, reviewers, and students."

### 3. Who it is for

Five cards, icon + one line each:

| Audience | One line |
|---|---|
| Institutes | Colleges and coaching centres running periodic entrance or scholarship tests. |
| Universities | Degree programmes needing high-volume, proctored-style semester assessments. |
| Corporate assessments | Pre-employment and certification testing for recruiters. |
| Recruitment | Bulk screening with automated evaluation and result publication. |
| Certification | Bodies issuing scored credentials with published, auditable results. |

### 4. Capabilities

Seven cards, one per capability, each with a short sentence:

1. **Configurable exam engine** — blueprint, selection rules, schedules, and timed delivery.
2. **Question bank with LaTeX rendering** — mathematics and science rendered from
   MARKDOWN_LATEX_V1 content.
3. **Three exam patterns** — NTA, TCS iON, and IBPS-style formats are first-class.
4. **Analytics** — student, assessment, question, topic, and organization insights.
5. **Commerce** — access packages, subscriptions, purchases, and entitlement-gated start.
6. **Communication** — notifications, announcements, and email delivery.
7. **Reporting** — 23 report types, CSV + PDF, with protected download.

### 5. How it works

Four-step diagram, horizontal on desktop, vertical on mobile:

**Author → Assess → Deliver → Evaluate**

- **Author** — staff and reviewers author questions and build blueprints/assessments.
- **Assess** — administrators schedule assessments against selection rules.
- **Deliver** — the runtime engine serves the timed attempt to students, with offline queue.
- **Evaluate** — answers are evaluated from the answer key and results/scorecards are published.

### 6. Pricing / trial

- **Purpose:** informational only; no checkout in V1.5.
- **Copy sketch:** "Pricing is tailored to your scale. Contact us for pricing and a trial."
- **CTA:** "Contact us" → scrolls to the Contact form. **No payment UI, no checkout.**

### 7. Testimonials / credibility

- **Status: DEFERRED.** Omitted from the V1.5 landing page until real customers exist. We do
  not invent fake customers or quotations. When a real customer is available, this section
  returns as a single-row proof bar (name, role, one quote) — added under a new ADR.

### 8. FAQ

Five questions (accordion, one open at a time):

1. Who runs the platform for us? — You do. Your SUPER_ADMIN creates the organization and its
   ADMIN; the ADMIN manages users and content.
2. Does it support math and science questions? — Yes; the question bank renders LaTeX.
3. Which exam patterns are supported? — NTA, TCS iON, and IBPS-style.
4. Can we take payments? — Reference adapters are active in V1.5; real provider adapters are
   not yet implemented (GAP-099, GAP-108).
5. How do we get started? — Contact us for a trial; we set up your organization and first admin.

### 9. Contact

- **Purpose:** capture a lead without creating a user or writing business data.
- **API:** `POST /api/v1/public/contact` (public, rate-limited per source IP; ADR-0037).
- **Fields:** name, email, message; organization (optional).
- **States:** idle → submitting → success ("Thanks — we'll be in touch.") → error
  (validation, rate-limit "too many requests", or network).
- **Validation:** required name/email/message; email format checked client-side and server-side.

### 10. Footer

Links: `/docs`, `/docs/terms`, `/docs/privacy`, `/docs/support`, `/login` (→ `/login/student`).

## Open questions

- **Testimonials** — none yet; the section is omitted (approved) rather than shipped as a
  placeholder, so the page never displays invented customers.
