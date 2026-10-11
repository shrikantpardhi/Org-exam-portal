# ADR-0007: Configuration-driven UI (CDUI) boundary

- **Status:** Accepted
- **Date:** 2026-10-07
- **Frozen source:** `specs/06-Architecture-Freeze-V1.5.md §4, §109`,
  `specs/08-Exam-Engine-and-CDUI-Contract-V1.5.md`

## Decision

Examination behaviour is driven by configuration, not by exam-specific code. The frontend does
not branch on exam name. The backend resolves an exam engine from persisted assessment
configuration and returns a normalized delivery configuration; the frontend renders
capabilities from that configuration.

P0 establishes only the boundary:

- The frontend provider/location and the Zod schema location for future delivery
  configuration.
- No engine implementations (`TCS_ION`, `IBPS`, `NTA`, `UPSC`, `RAILWAY`, `CUSTOM`) — those
  belong after P1.
- The exam engine contract is **not** implemented in P0.

The rule "configuration for *what the examination should do*; code for *how the platform
implements it*" governs all future work.

## Consequences

- No hard-coded exam names in frontend or backend code.
- No generic JSON configuration engine that maps arbitrary blobs to behaviour.
