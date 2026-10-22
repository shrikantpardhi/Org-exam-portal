# Model Routing

## Free-first order

1. Try the cheapest model that has finished this *shape* of task before.
2. Review its output.
3. Escalate only if review fails.

## Routing table

| Task shape | First choice | Fallback | Do not use |
|---|---|---|---|
| Dockerfile, Compose, YAML | Cheapest fast (Cat Jam) | GLM Flash | Frontier |
| CI workflow | Cheapest fast | GLM Flash | Frontier |
| Shell script, backup, rollback | Laguna S 2.1 | DeepSeek Flash Fast | Frontier |
| Frontend component / CRUD screen | Cat Jam | Ling 3.0 Flash | Frontier |
| Frontend form + validation | Cat Jam | Ling 3.0 Flash | Frontier |
| Backend state / transaction / concurrency | DeepSeek V4 Flash Fast | Laguna S 2.1 | Cheapest |
| Backend orchestration across modules | Laguna S 2.1 | DeepSeek Flash Fast | Cheapest |
| Schema amendment | Frontier (Qwen Max / Beam) | — | Cheap |
| Security boundary change | Frontier | — | Cheap |
| Product rule not yet in an ADR | Frontier | — | Cheap |
| Test authoring | Cheapest fast | Laguna S 2.1 | Frontier |
| Documentation, git, commit messages | Cheapest fast | — | Frontier |

## Cost rules

- One frontier call ≈ 20× one cheap call. Use frontier for decisions, not for code.
- A failed cheap retry is still cheaper than a frontier success. Retry once before escalating.
- Never run a frontier model on a task the routing table marks as cheap.
- Watch the 5-hour window. If it is nearly exhausted, switch remaining volume work to the cheapest model rather than waiting.
- Frontier tasks (schema amendment, security boundary) go at the **start** of a window, not the end.

## Escalation triggers

Escalate when any of these is true:

- The cheap model produced code that fails `scripts/verify.sh` twice.
- The task requires a schema amendment.
- The task changes an authorization boundary.
- The task requires a product decision not in an ADR.
- The task requires reasoning across more than two module specs at once.