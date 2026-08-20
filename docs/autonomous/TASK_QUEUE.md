# TASK_QUEUE

Prioritized backlog. **Architect** invents / refines, **Boss** orders NOW, **Worker** implements.

Status legend: `todo` | `doing` | `done` | `blocked` | `deferred`

**Boss rule:** at most **1–3** tasks in `doing` / NOW. Worker takes only the NOW block.  
**Architect rule:** docs-only edits; do not fight Worker on Kotlin files.

---

## NOW (Worker — start immediately)

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P0 | T040 | todo | Worker | **Fix Hub delete-by-substring (1.5.2).** In `HubActivity.deleteAccount`, match only by steamId / exact mafile filename / account id — never by `accountName` substring inside mafile JSON body. Bump `versionName`→`1.5.2` and `versionCode`→`150002`. Append DEV_LOG; commit + push `development`. Do **not** edit CSFloat or Steam confirmation poll paths. |

### Acceptance (T040)

- [ ] Repro: two accounts where one’s display name appears inside the other’s mafile → delete A never removes B
- [ ] Regression: normal swipe-delete still works
- [ ] Version `1.5.2` / `150002`
- [ ] DEV_LOG + push

### Acceptance (T010) — **Boss APPROVED** (`b3bb468` on `origin/development`)

- [x] `.../csfloat/` sources (models, client, settings, secure store, WM skeleton)
- [x] Default OFF; scheduler no-ops without enabled+key
- [x] Version `1.5.1` / `versionCode` **150001**
- [x] `CsFloatSaleWorker` CSFloat-HTTP only — no Steam getlist/confirm
- [x] Manual Load confirmations / AUTO-on-manual-load unchanged
- [x] Commit on `origin/development`

---

## NEXT (queue after T040 — Boss promotes one at a time)

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P0 | T011 | todo | — | **CSFloat Test connection + credentials polish.** Add **Test connection** → `CsFloatClient.me()` with distinct ok/401/429/network UX; never log key. Verify disable/clear cancels `CsFloatScheduler`. Main CSFloat dialog only — not Hub delete. |
| P0 | T012 | todo | — | **CSFloat read-only pending sales (foreground).** When enabled+key: show queued/pending count + list (item name, price cents, state, buyer steamId). Refresh button only (no background accept). Empty/error states clear. |
| P1 | T013 | todo | — | **CSFloat notification: actionable trades.** WorkManager (enabled accounts only) cheap-probes `/me`; if `actionable_trades`>0 (or queued), post notification opening the pending screen. Respect interval + battery-not-low. **Still no Steam Guard calls.** |
| P1 | T020 | todo | — | **Opt-in accept + Steam offer send.** User (or explicit “auto-accept sales” second toggle, default OFF) accepts queued CSFloat trades, creates Steam offer, posts steam-status. Enqueue offer id for later confirm — **do not auto-confirm yet.** |
| P1 | T021 | todo | — | **Safe CSFloat Guard confirm.** Confirm **only** whitelisted offer ids from T020; match creator_id; floors ≥35s getlist; drop on asset mismatch; audit log. Never blanket accept-all from worker. |
| P2 | T022 | todo | — | **Dual-bot conflict + sales audit.** Detect/warn if pending sales change without local accept; show last CSFloat check time; optional “pause MSDA CSFloat” one-tap. |

### Acceptance (T011)

- [ ] Opt-in UI reachable from account context; default OFF (already in WIP — verify after commit)
- [ ] API key never written to plaintext prefs / logs / mafile export
- [ ] **Test connection** button: live `/me`; distinct UX for ok / 401 / 429 / network
- [ ] Disabling or clearing key cancels `CsFloatScheduler` for empty ready set
- [ ] Existing Steam flows untouched
- [ ] DEV_LOG + push; version patch if shipping UI alone after 1.5.1

### Acceptance (T012)

- [ ] List parses `queued`/`pending` trades used by botCsFloat
- [ ] No POST accept / no Steam offer / no Guard from this screen
- [ ] Pull/refresh is user-initiated only
- [ ] Works offline-failure without crashing Main confirmation UI

### Acceptance (T013)

- [ ] Worker runs only for `readySteamIds()` (enabled + key)
- [ ] Min interval ≥15 min; default 30
- [ ] Notification does not call Steam APIs
- [ ] Doze-safe (WorkManager); no AlarmManager 1s loops

### Acceptance (T020–T021) — summary

- [ ] Second toggle for auto-accept (default OFF) separate from “CSFloat enabled”
- [ ] Guard path uses offer whitelist only; PROTOCOL floors respected
- [ ] Unit/instrumentation or clearly documented manual test checklist in DEV_LOG

---

## UI polish (real-user value)

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P1 | T030 | todo | — | **Widget tap-to-copy 2FA** + brief Toast; optional auto-clear clipboard after 60s. Fix countdown `-1` when bound by steamId (`Code2FAWidgetProvider`). |
| P1 | T031 | todo | — | **Hub search/filter** by account name + label; keep swipe-delete but delete by **exact mafile/account id**, not substring body match (`HubActivity.deleteAccount`). |
| P2 | T032 | todo | — | **Session health chip** on Hub/Main: OK / needs login / renewing — reduce surprise confirmation failures. |
| P2 | T033 | todo | — | **Material confirmation rows:** Coil/Glide icons (cancel on detach), clearer accept/decline affordances; replace raw `Thread`+`URL.openStream` in `MainActivity`. |
| P2 | T034 | todo | — | **Code display polish:** monospace + letter-spacing; progress maps remaining seconds accurately (`activity_main` / `refreshCodeViews`). |
| P3 | T035 | todo | — | **Settings sections** (security / backup / network / about) — Preference-style grouping without redesigning flows. |

### Acceptance (T030)

- [ ] Tap widget copies current code; works when app backgrounded
- [ ] Countdown shows 0–30 correctly for steamId-bound widgets
- [ ] No 1s exact-alarm battery regression beyond current behavior (prefer align to period boundary if touched)

### Acceptance (T031)

- [ ] Filter updates list live; empty state message
- [ ] Delete removes only the intended account file
- [ ] Long-press copy 2FA still works

---

## Bugs / tech debt (high value)

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P0 | T040 | doing | Worker | **Fix Hub delete-by-substring** — see NOW |
| P1 | T041 | todo | — | **Pace accept-all / trade auto-confirm** — ≥300–500ms between Steam ops + stop/backoff on failure/429 (`MainActivity`, `ConfirmationService`). |
| P1 | T042 | todo | — | **Wire or remove dead market/gift auto-confirm** APIs in `AppSettings` (menu already hints market) — either UI+behavior or delete dead flags. |
| P2 | T043 | todo | — | **PasswordManager case bug:** `hasPassword` case-sensitive vs `getPassword` case-insensitive — unify. |
| P2 | T044 | todo | — | **Remove or quarantine dead background sync** (`BackgroundSyncScheduler`, `ConfirmationBackgroundWorker`) OR document why kept disabled — reduce confusion before CSFloat WorkManager. |
| P2 | T045 | todo | — | **Proxy passwords in plaintext `msda_ui`** — move to Keystore-backed store like sessions. |
| P3 | T046 | todo | — | Delete or fix orphaned `PasswordBackupHelper` (db path vs live prefs). |

### Acceptance (T040)

- [ ] Repro: two accounts where one’s display name appears inside the other’s mafile → delete A never removes B
- [ ] Regression: normal swipe-delete still works

### Acceptance (T041)

- [ ] Accept-all with N>3 confirmations does not fire N Steam calls with zero delay
- [ ] On hard failure, remaining items stop or user is prompted — no silent hammering

---

## Smaller authenticator features

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P2 | T050 | todo | — | Clipboard auto-clear after copy from Hub long-press / Main code tap (30–60s). |
| P2 | T051 | todo | — | Export mafile warning dialog: “contains Guard secrets — share only with yourself”. |
| P3 | T052 | todo | — | PIN attempt lockout / backoff after N failures (`LockActivity`). |

---

## Done / deferred

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P0 | T010 | done | Worker | CSFloat Phase-1 scaffold + 1.5.1 (`com.msda.android.csfloat`, opt-in stubs, WorkManager skeleton) |
| P0 | T001 | done | Architect | Review PROTOCOL + CSFLOAT_NOTES; first slice = T010 |
| P1 | T002 | done | Architect | Settings shape documented in CSFLOAT_NOTES + mirrored by scaffold (`enabled` / interval / secure key) |
| P1 | T003 | deferred | — | Spike Guard-vs-UX — fold into T021 design; keep deferred until T012 lands |
| P2 | T004 | done | Architect | Battery/network budget written in CSFLOAT_NOTES |

## Boss notes

- Steam-safety gate: any Worker change that adds confirmation **timer** polling → reject.
- **Boss Cycle 2:** T010 **APPROVED** (`b3bb468`). WorkManager for CSFloat API ≥15m / opt-in / battery-not-low is OK; Steam Guard spam is not.
- Priority order: bugfixes before more CSFloat — single NOW = **T040** (Hub). Then **T011** (Main CSFloat dialog Test connection).
- T021 design sketch in `CSFLOAT_NOTES.md` — implement only after T012/T020.
- Coordinate via docs; do not fight Worker on the same Kotlin files.
