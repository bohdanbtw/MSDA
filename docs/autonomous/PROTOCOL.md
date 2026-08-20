# PROTOCOL — Autonomous MSDA development

Short rules for Architect / Boss / Worker cycles.

## Git

- Work **only** on the `development` branch.
- **Never** force-push.
- **Never** merge to / rewrite `master` from this workflow (Boss/human owns master merges).
- Prefer **small incremental commits**, pushed to `origin/development` after each meaningful batch.

## Product safety

- Do **not** break existing Steam session / login / manual confirmation flows.
- **No aggressive Steam confirmation polling** (rate limits hit before). Guard confirm only when a real action needs it (e.g. a CSFloat sale trade), not on a timer.
- **CSFloat integration** must be **opt-in per account**, respectful of battery and network.

## Versioning

- Bump version on each meaningful commit batch:
  - **patch** — small fixes / docs / hardening
  - **minor** — user-visible features
- Version lives in `AndroidCppApp/packaging/gradle.properties` (`app.version.name` / `app.version.code`); do not hard-code elsewhere.

## Cycle hygiene

- After each cycle, **append** to `docs/autonomous/DEV_LOG.md` (what changed, why, follow-ups).
- Update `TASK_QUEUE.md` statuses when picking up or finishing work.
