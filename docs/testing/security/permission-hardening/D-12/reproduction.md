# D-12 — The SUPER_ADMIN permission set is not pinned by any test

| Field | Value |
|---|---|
| Defect | D-12 |
| Severity | HIGH |
| Status | **Confirmed — real** |
| Reproduced | 2026-10-26, by searching the test tree |
| Frozen contract | ADR-0032 Consequences ("The platform permission catalogue (`PLATFORM_*`) is granted to `SUPER_ADMIN` only"); `docs/frontend/super-admin-flow.md` §12–13 (seeds screens imply `ROLE_VIEW` / `PERMISSION_VIEW`); ADR-0030 precedent (test assertions being *rewritten* when a frozen contract's intent is clarified) |
| Contract conflict | ADR-0032 says "`PLATFORM_*` only". The seeds screens require `ROLE_VIEW` and `PERMISSION_VIEW`. Both statements are in the repository. **Neither is asserted anywhere**, so the disagreement is invisible. |

## Reproduction

~~~text
grep -rn 'PLATFORM_' backend/src/test/java/com/company/exam/integration/security/   -> 0 matches
~~~

The closest existing coverage is `SuperAdminImmutabilityIntegrationTest`, which has four
`SUPER_ADMIN` references and asserts only `SUPER_ADMIN_IMMUTABLE` on the **user** (archive and
role-replacement paths). No test enumerates the role's permissions.

~~~text
grep -rn 'SUPER_ADMIN' backend/src/test/java/.../SuperAdminImmutabilityIntegrationTest.java
  14: * SUPER_ADMIN immutability (ADR-0032, 20-P11 §18): the installation owner cannot be archived or
  30: .andExpect(jsonPath("$.errors[0].code").value("SUPER_ADMIN_IMMUTABLE"));
  43: .andExpect(jsonPath("$.errors[0].code").value("SUPER_ADMIN_IMMUTABLE"));
  48: syntheticEmail("super-admin"), PASSWORD, Status.ACTIVE, "SUPER_ADMIN");
~~~

## Why this is HIGH and not merely "nice to have"

Every other defect in this inventory is a permission-set question: what does a role hold, and who
may change it? With no pinned set, a defect like D-01 is undetectable and a silent fix (or a silent
regression) is equally undetectable. This test is the instrument that makes the rest of the mission
verifiable, and the brief correctly places it last among the in-scope tasks so it pins the finished
state.

## Blast radius

None at runtime. It is entirely a verifiability defect: the contract cannot be checked.

## Residual if not fixed
Any future migration, seed, or manual grant can change the install owner's capability with no test
failing.

## Fix class
Test only. No ADR, no migration.

## Gate
None. In-scope for Task 6.