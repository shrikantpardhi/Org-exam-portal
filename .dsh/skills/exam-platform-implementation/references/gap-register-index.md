# Gap Register — How to Read It

`docs/known-gaps.md` has one row per gap. The row is the entire record. Do not open the register and read every row; grep for the area.

## Row shape

| Field | Meaning |
|---|---|
| ID | `GAP-nnn` |
| Area | Module or concern |
| Gap | What is missing or contradictory |
| Impact | What breaks without it |
| Reason / frozen constraint | Why it exists (usually a spec section) |
| Target | Phase or condition that closes it |
| Status | Open, Deferred, By-design, Closed, Blocking |

## Lookup

```bash
scripts/gap-lookup.sh <keyword>
```
Example: scripts/gap-lookup.sh deadline returns GAP-062 and its status.

Rules
- Before planning, grep for the area. If a gap already covers it, do not re-derive the constraint.
- After closing a gap, update its Status and add the proving test name.
- After opening a gap, add a new row with all seven fields. Do not leave it in a chat message.
- Never close a gap without the test that proves it. The register is the audit trail.


---

## 6. `references/stop-conditions.md`

```markdown
# Stop Conditions

Stop the task and report. Do not proceed, do not work around, do not "just this once."

## Stop if the task requires…

- a table, column, or index not in `specs/04-Database-Schema-V1.5.md`
- an endpoint not in `specs/05-API-Contract-V1.5.md`
- a change to the ExamEngine contract
- a change to the CDUI capability set
- a change to `answer_key_detail` as the correctness source
- a change to the AccessGrant authorization model
- a change to the snapshot readiness or Start Exam boundary
- a product decision not yet recorded in an ADR
- weakening an existing test to make a build pass
- disabling a security control to unblock a feature

## Report format when stopping
STOPPED at: <step>
Reason: <one sentence>
Spec gap: <specs/xx §yy> or "no spec section covers this"
Options: <one or two, each one line>
Needs: <ADR approval | schema amendment | product decision>


## After the report

Do not implement a workaround. Wait for approval. The task remains open in the register.

