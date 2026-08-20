# TASK_QUEUE

Prioritized backlog. **Architect** invents / refines, **Boss** orders NOW, **Worker** implements.

Status legend: `todo` | `doing` | `done` | `blocked` | `deferred`

**Boss rule:** at most **1–3** tasks in `doing` / NOW. Worker takes only the NOW block.  
**Architect rule:** docs-only edits; do not fight Worker on Kotlin files.

---

## NOW (Worker)

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P0 | T010 | doing | Worker | **CSFloat Phase-1 scaffold + 1.5.1.** Package `com.msda.android.csfloat`: models, `CsFloatClient` (`/me` + 429 handling), `CsFloatAccountSettings` (default OFF), `CsFloatSecureStore`, optional WorkManager skeleton that **does nothing** unless enabled+key. Version `1.5.1` / `150001` (or `150100` if already bumped). **No** Steam confirmation timer/poll spam. Append DEV_LOG; commit + push `development`. |

### Acceptance (T010)

- [ ] `.../csfloat/` sources compile in `assembleDebug`
- [ ] Default: CSFloat disabled for all accounts; no network from cold start without opt-in
- [ ] Version name `1.5.1` (code ≥ previous)
- [ ] Manual Load confirmations / trade AUTO-on-manual-load unchanged
- [ ] No new Steam `getlist` polling loop
- [ ] DEV_LOG + push

---

## NEXT (queue after T010 — Boss promotes one at a time)

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P0 | T011 | todo | — | **CSFloat credentials UI (per account).** Entry from account Main menu or Settings: toggle enable, API key field (masked), poll interval (15–240 min, default 30), **Test connection** → `GET /me` shows username/balance or error. Save key only via secure store. Cancel WorkManager when disabled/cleared. |
| P0 | T012 | todo | — | **CSFloat read-only pending sales (foreground).** When enabled+key: show queued/pending count + list (item name, price cents, state, buyer steamId). Refresh button only (no background accept). Empty/error states clear. |
| P1 | T013 | todo | — | **CSFloat notification: actionable trades.** WorkManager (enabled accounts only) cheap-probes `/me`; if `actionable_trades`>0 (or queued), post notification opening the pending screen. Respect interval + battery-not-low. **Still no Steam Guard calls.** |
| P1 | T020 | todo | — | **Opt-in accept + Steam offer send.** User (or explicit “auto-accept sales” second toggle, default OFF) accepts queued CSFloat trades, creates Steam offer, posts steam-status. Enqueue offer id for later confirm — **do not auto-confirm yet.** |
| P1 | T021 | todo | — | **Safe CSFloat Guard confirm.** Confirm **only** whitelisted offer ids from T020; match creator_id; floors ≥35s getlist; drop on asset mismatch; audit log. Never blanket accept-all from worker. |
| P2 | T022 | todo | — | **Dual-bot conflict + sales audit.** Detect/warn if pending sales change without local accept; show last CSFloat check time; optional “pause MSDA CSFloat” one-tap. |

### Acceptance (T011)

- [ ] Opt-in UI reachable from account context; default OFF
- [ ] API key never written to plaintext prefs / logs / mafile export
- [ ] Test connection uses live `/me` and surfaces 401/429/network distinctly
- [ ] Disabling clears schedule for that steamId
- [ ] Existing Steam flows untouched

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
| P0 | T040 | todo | — | **Fix Hub delete-by-substring** — match account by steamId/filename, not `accountName` appearing in mafile JSON body. |
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
| P0 | T001 | done | Architect | Review PROTOCOL + CSFLOAT_NOTES; first slice = T010 |
| P1 | T002 | done | Architect | Settings shape documented in CSFLOAT_NOTES + mirrored by scaffold (`enabled` / interval / secure key) |
| P1 | T003 | deferred | — | Spike Guard-vs-UX — fold into T021 design; keep deferred until T012 lands |
| P2 | T004 | done | Architect | Battery/network budget written in CSFLOAT_NOTES |

## Boss notes

- Steam-safety gate: any Worker change that adds confirmation **timer** polling → reject.
- Prefer promoting **T040** (delete bug) or **T011** (CSFloat UI) immediately after T010 lands — Architect recommends **T011** if CSFloat is the product bet, else **T040** for quick user trust.
- Coordinate via docs; Architect will not edit Worker Kotlin WIP.
