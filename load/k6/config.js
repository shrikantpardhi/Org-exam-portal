/**
 * Shared configuration for the P7 load harness
 * ({@code specs/16-P7-Pilot-V1.5.md §24-§35, §44-§48}).
 *
 * <p>Everything the harness needs is injected through environment variables so a run is reproducible
 * from the runbook: the same env plus the same seed reproduces the same workload.
 */

const env = (name, fallback) => (__ENV[name] === undefined ? fallback : __ENV[name]);

export const BASE_URL = env("P7_BASE_URL", "http://localhost:8081");
export const PROFILE = env("P7_PROFILE", "start");
export const USERS = Number(env("P7_USERS", "100"));
export const SCHEDULE_ID = env("P7_SCHEDULE_ID", "");
export const QUESTIONS_PER_ATTEMPT = Number(env("P7_QUESTIONS", "100"));

/** The credential manifest produced by `load/seed.mjs` (JSON array of {email, password, scheduleId}). */
export const CREDENTIALS_FILE = env("P7_CREDENTIALS_FILE", "load/out/credentials.json");

/**
 * Auto-save policy under test ({@code specs/14-P5-Delivery-V1.5.md §42}).
 *
 * <p>The effective interval is read from the server's own normalized configuration at runtime; this
 * value is only the harness's pacing assumption for profile C. If they disagree the runbook says to
 * trust the server, not this number.
 */
export const AUTOSAVE_INTERVAL_SECONDS = Number(env("P7_AUTOSAVE_INTERVAL", "10"));

/** How long profile B/C/D/E run, in seconds. */
export const DURATION_SECONDS = Number(env("P7_DURATION_SECONDS", "60"));

/**
 * Starting index into the credential manifest.
 *
 * <p>The ramp must not reuse students across steps: a student who has already submitted cannot start
 * again, so a step that replayed an earlier cohort would measure "Attempt is already submitted" rather
 * than load. Each step therefore gets a disjoint slice `[OFFSET, OFFSET + USERS)`, which is set-up rather
 * than a data mutation between steps.
 */
export const OFFSET = Number(env("P7_OFFSET", "0"));

/** Request timeout, generous enough not to mask a slow-but-working endpoint as an error. */
export const TIMEOUT = env("P7_TIMEOUT", "30s");

/** Enabled only when the operator has confirmed the target is disposable (§13). */
export const ALLOW_MUTATION = env("P7_ALLOW_MUTATION", "true") === "true";

/**
 * Thresholds mirror P12 §9 exactly, so a passing run maps directly onto the frozen gate rather than a
 * harness-specific invention.
 */
export const THRESHOLDS = {
  // p95 < 100 ms for applicable synchronous endpoints (P7 §45).
  "http_req_duration{applicable:true}": ["p(95)<100"],
  // Submission success > 99%; the custom rate is tagged only on submit calls.
  submission_success_rate: ["rate>0.99"],
  // Exam failure < 1% — a combined signal defined in P7 §48.
  exam_failure_rate: ["rate<0.01"],
};
