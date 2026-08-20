# TASK_QUEUE

Prioritized backlog. **Architect** fills items, **Boss** orders execution, **Worker** marks done.

Status legend: `todo` | `doing` | `done` | `blocked` | `deferred`

**Boss rule:** at most **1–3** tasks in `doing` / assigned NOW. Worker takes only the NOW block.

---

## NOW (Worker — start immediately)

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P0 | T010 | todo | Worker | **CSFloat Phase-1 scaffold + release 1.5.1.** Create `com.msda.android.csfloat` package: data models, `CsFloatApiClient` stub (OkHttp/Retrofit-style, no live calls required), `CsFloatAccountSettings` (enable flag + API key placeholders, default OFF). Bump `versionName`→`1.5.1` and `versionCode`→`150001` in `AndroidCppApp/packaging/app/build.gradle`. **Do not** change Steam login/session/confirmation polling. **Do not** add WorkManager or timers. Wire nothing into MainActivity confirmation refresh. Append DEV_LOG; commit + push `development`. |

### Acceptance (T010)

- [ ] New Kotlin files under `.../csfloat/` compile in debug APK build
- [ ] Version is 1.5.1 / 150001
- [ ] No edits that increase Steam confirmation poll frequency
- [ ] Existing manual Load confirmations / AUTO-on-manual-load behavior unchanged
- [ ] DEV_LOG entry for the batch

---

## Active (max 2 more — parked until T010 done)

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P1 | T002 | todo | — | Define per-account CSFloat opt-in settings shape (finalize after scaffold lands; may merge into T010) |
| P1 | T003 | deferred | — | Spike: Guard confirm only on real CSFloat sale (no timer) vs current confirmation UX — **after** T010 |

## Backlog

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P0 | T001 | done | Architect | Review PROTOCOL + CSFLOAT_NOTES; first slice = T010 scaffold |
| P2 | T004 | todo | — | Document battery/network budget for any future Android background CSFloat work |
| P2 | T011 | todo | — | CSFloat settings UI (per-account enable + API key) — after T010 |
| P2 | T012 | todo | — | Foreground CSFloat pending-sales screen — after T011 |

## Boss notes (2026-08-21)

- Unblocked Worker: docs were untracked; CSFLOAT_NOTES was missing — Boss restored notes + assigned **single NOW = T010**.
- Steam-safety review gate: any Worker PR that adds confirmation polling loops → **reject**, re-queue fix.
- Coordinate via docs only; avoid simultaneous edits to `MainActivity.kt` confirmation paths.
