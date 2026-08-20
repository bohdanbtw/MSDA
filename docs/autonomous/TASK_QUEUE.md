# TASK_QUEUE

Prioritized backlog. **Architect** fills items, **Boss** orders execution, **Worker** marks done.

Status legend: `todo` | `doing` | `done` | `blocked` | `deferred`

**Boss rule:** at most **1–3** tasks in `doing` / assigned NOW. Worker takes only the NOW block.

---

## Worker NOW

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P0 | T010 | doing | Worker | Scaffold `com.msda.android.csfloat` + settings toggle stubs + WorkManager skeleton; bump version to **1.5.1**. Do **not** change Steam login/session or add Guard confirmation timer/poll spam. Append DEV_LOG; commit + push `development`. |

### Acceptance (T010)

- [ ] New Kotlin under `.../csfloat/` (package + settings toggle stubs + WorkManager skeleton)
- [ ] Version bumped to 1.5.1
- [ ] No increase in Steam confirmation poll frequency
- [ ] Existing manual Load confirmations / AUTO-on-manual-load unchanged
- [ ] DEV_LOG entry for the batch

---

## Backlog (T001–T004)

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P0 | T001 | done | Architect | Review `PROTOCOL.md` + `CSFLOAT_NOTES.md`; propose first CSFloat opt-in slice for MSDA (= T010) |
| P1 | T002 | todo | — | Define per-account CSFloat opt-in settings shape (API key storage, poll interval, enable flag) |
| P1 | T003 | deferred | — | Spike: confirm Guard only on real CSFloat sale (no timer poll) vs current MSDA confirmation UX — after T010 |
| P2 | T004 | todo | — | Document battery/network budget for any Android background CSFloat work |

## Later

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P2 | T011 | todo | — | CSFloat settings UI (per-account enable + API key) — after T010 |
| P2 | T012 | todo | — | Foreground CSFloat pending-sales screen — after T011 |

## Notes

- Fill new rows above the fold; keep priorities strict.
- Boss reorders / assigns before Worker starts a cycle.
- Worker moves status to `doing` → `done` and appends a DEV_LOG entry each cycle.
- Steam-safety gate: confirmation polling loops → reject.