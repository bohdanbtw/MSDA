# TASK_QUEUE

Prioritized backlog. **Architect** invents / refines, **Boss** orders NOW, **Worker** implements.

Status legend: `todo` | `doing` | `done` | `blocked` | `deferred`

**Boss rule:** at most **1–3** tasks in `doing` / NOW. Worker takes only the NOW block.  
**Architect rule:** docs-only edits; do not fight Worker on Kotlin files.

---

## NOW (Worker — start immediately)

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| — | — | idle | — | **Awaiting Boss.** Recommend **T012** (pending sales) or **T060** (session renewal toggle). |

### Acceptance (T011) — done 2026-08-21

- [x] Test connection in Main CSFloat dialog → `/me` with ok/401/429/network UX
- [x] Clear key + scheduler cancel when ready set empty; never log key
- [x] Version `1.5.5` / `150005`

### Acceptance (T041) — done 2026-08-21

- [x] Accept-all / trade auto-confirm wait **400ms** between Steam ops
- [x] Hard failure or 429-like error **stops** remaining accepts (no silent hammering)
- [x] Single accept/decline unchanged; Load path unchanged aside from paced auto-trades
- [x] Version `1.5.4` / `150004`; DEV_LOG + push

### Acceptance (T061) — **Boss APPROVED** (`6dc2651`, v1.5.3)

- [x] Confirmation HMAC uses `TimeAligner` (cached offset)
- [x] No confirmation timer polling; getlist still user/event driven
- [x] Version `1.5.3` / `150003`
- [ ] Follow-up (non-blocking): tighten `looksLikeClockSkew` — `"invalid"` alone is broad

---

## NEXT (after T011)

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P0 | T012 | todo | — | **CSFloat read-only pending sales (foreground).** Queued/pending list; user refresh only; no accept/offer/Guard. |
| P0 | T060 | todo | — | **Opt-in proactive session renewal.** Wire dead `SessionRenewalManager.schedule()` behind Settings toggle (default OFF). Renew near-expiry with refresh token; never getlist. |
| P1 | T013 | todo | — | **CSFloat notification: actionable trades.** WorkManager cheap `/me` probe; notify only; no Steam Guard. |
| P1 | T063 | todo | — | **Confirm All type breakdown + trade friction.** Dialog shows market/trade/other counts; trades need extra checkbox (default off). |
| P1 | T020 | todo | — | **Opt-in accept + Steam offer send** (second toggle default OFF); enqueue offer id; no auto Guard. |
| P1 | T021 | todo | — | **Safe CSFloat Guard confirm** — whitelist only; floors; audit (see CSFLOAT_NOTES T021 sketch). |
| P1 | T065 | todo | — | **Import conflict by SteamID** — Replace / Keep both / Cancel; never silent filename clobber of different accounts. |
| P1 | T064 | todo | — | **Dual SDA export:** Secrets-only vs Full SessionData chooser. |
| P1 | T042 | todo | — | **Wire or remove** dead market/gift auto-confirm APIs in `AppSettings`. |
| P2 | T022 | todo | — | Dual-bot conflict + sales audit UI. |
| P2 | T062 | todo | — | Encrypt mafile secrets at rest (Keystore); plaintext import migrates; export still SDA-compatible. |
| P2 | T066 | todo | — | Period-aligned 2FA UI tick (Main + widget) — cut 1 Hz wakeups. |
| P2 | T067 | todo | — | Hub multi-select: renew selected + disable auto-confirm panic switch. |

### Acceptance (T012) / (T013) / (T020–T021)

- Unchanged from prior Architect pass — see older checklists in DEV_LOG Cycle 1 if needed; keep PROTOCOL floors.

### Acceptance (T060)

- [ ] Settings toggle default OFF; OFF cancels WorkManager
- [ ] Worker renews only accounts with usable refresh path; does not change native active account incorrectly
- [ ] Zero Steam Guard getlist traffic from this worker

### Acceptance (T061) — see Boss APPROVED above

### Acceptance (T011)

- [ ] Test connection button: live `/me`; distinct ok / 401 / 429 / network
- [ ] API key never in plaintext prefs / logs
- [ ] Steam confirmation flows untouched
- [ ] Patch version bump when shipped

### Acceptance (T063)

- [ ] Accept-all dialog lists counts by confirmation type
- [ ] Including trades requires explicit checkbox (default unchecked)
- [ ] Single-item accept/decline unchanged

### Acceptance (T065)

- [ ] Same steamId import prompts Replace / Keep both / Cancel
- [ ] Replace leaves one mafile per steamId
- [ ] Different steamId + same filename warned before overwrite

---

## UI polish (real-user value)

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P1 | T030 | todo | — | **Widget tap-to-copy 2FA** + Toast; fix countdown `-1` for steamId binding (`Code2FAWidgetProvider`). |
| P1 | T031 | todo | — | **Hub search/filter** by name + label (after T040 delete fix). |
| P2 | T032 | todo | — | **Session health chip** on Hub/Main. |
| P2 | T033 | todo | — | Material confirmation rows + Coil/Glide (cancel on detach). |
| P2 | T034 | todo | — | Code display monospace + letter-spacing; accurate progress. |
| P3 | T035 | todo | — | Settings Preference-style sections. |

### Acceptance (T030) / (T031) — unchanged (widget copy; filter + safe delete).

---

## Bugs / tech debt

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P1 | T041 | done | Worker | Pace accept-all / trade auto-confirm — 400ms gap + stop on failure (**1.5.4**) |
| P1 | T042 | todo | — | Wire or remove market/gift auto-confirm dead API |
| P2 | T043 | todo | — | PasswordManager case: unify `hasPassword` / `getPassword` |
| P2 | T044 | todo | — | Quarantine/remove dead `BackgroundSyncScheduler` / `ConfirmationBackgroundWorker` |
| P2 | T045 | todo | — | Proxy passwords → Keystore store |
| P3 | T046 | todo | — | Fix/delete orphaned `PasswordBackupHelper` |

### Acceptance (T041)

- [ ] Accept-all N>3 does not fire N Steam calls with zero delay
- [ ] Hard failure stops or prompts — no silent hammering

---

## Smaller authenticator features

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P2 | T050 | todo | — | Clipboard auto-clear after copy (30–60s). |
| P2 | T051 | todo | — | Export mafile secrets warning dialog. |
| P3 | T052 | todo | — | PIN attempt lockout / backoff. |

---

## Done / deferred

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P0 | T011 | done | Worker | CSFloat Test connection + clear key (**1.5.5**) |
| P1 | T041 | done | Worker | Pace accept-all / trade auto-confirm — 400ms gap + stop on failure (**1.5.4**) |
| P0 | T061 | done | Worker | Steam-aligned confirmation HMAC via cached `TimeAligner` (**1.5.3**) |
| P0 | T040 | done | Worker | Hub delete-by-substring → **1.5.2** (`5234051`) |
| P0 | T010 | done | Worker | CSFloat Phase-1 scaffold + 1.5.1 (`b3bb468`) |
| P0 | T001 | done | Architect | Review PROTOCOL + CSFLOAT_NOTES; first slice = T010 |
| P1 | T002 | done | Architect | Settings shape in CSFLOAT_NOTES + scaffold |
| P1 | T003 | deferred | — | Fold into T021 after T012 |
| P2 | T004 | done | Architect | Battery/network budget in CSFLOAT_NOTES |

## Boss / Architect notes

- Steam-safety gate: confirmation **timer** polling → reject.
- Worker shipped **T041** (1.5.4) + **T011** (1.5.5). Recommend next **T012** or **T060**.
- Architect docs-only; do not fight Worker on Kotlin during active NOW.
