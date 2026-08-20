# TASK_QUEUE

Prioritized backlog. **Architect** invents / refines, **Boss** orders NOW, **Worker** implements.

Status legend: `todo` | `doing` | `done` | `blocked` | `deferred`

**Boss rule:** at most **1–3** tasks in `doing` / NOW. Worker takes only the NOW block.  
**Architect rule:** docs-only edits; do not fight Worker on Kotlin files.

---

## NOW (Worker — Boss override: Steam-safety before CSFloat polish)

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P0 | T061 | todo | Worker | **Steam-aligned confirmation HMAC (1.5.3).** `ConfirmationService` still uses wall clock — switch `t`/`k` to `TimeAligner` offset (same as 2FA). Cache offset; skew hint on fail. Bump to `1.5.3` / `150003`. **No** getlist timer loops. DEV_LOG + push. |

### Acceptance (T061)

- [ ] Confirmation HMAC uses aligned Steam time (not raw `currentTimeMillis`)
- [ ] Offset cached (no QueryTime per item)
- [ ] Manual Load / AUTO-on-manual-load still work
- [ ] No new confirmation polling
- [ ] Version `1.5.3` / `150003`; DEV_LOG + push

### Acceptance (T040) — **Boss APPROVED** (`5234051`, v1.5.2)

- [x] Delete matches steamId / exact account_name / filename — no JSON body substring
- [x] Purge session/proxy/label/CSFloat for resolved steamId
- [x] Version 1.5.2 / 150002
- [x] On `origin/development`

---

## NEXT (after T061)

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P0 | T041 | todo | — | **Pace accept-all / trade auto-confirm** — ≥300–500ms between Steam ops + stop/backoff on 429. |
| P0 | T011 | todo | — | **CSFloat Test connection + credentials polish.** Add Test connection to Main CSFloat dialog: `CsFloatClient.me()` → username/balance or distinct 401/429/network. Never log key. |
| P0 | T060 | todo | — | **Opt-in proactive session renewal** — wire `SessionRenewalManager.schedule()` (currently only cancelled). |
| P0 | T012 | todo | — | **CSFloat read-only pending sales.** Expand `CsFloatTradeSummary` with buyer steamId, price cents, market_hash_name, asset_id, steam_offer id/state; foreground list + refresh. |
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

### Acceptance (T011)

- [ ] Test connection button: live `/me`; distinct ok / 401 / 429 / network
- [ ] API key never in plaintext prefs / logs / mafile export
- [ ] Disabling or clearing key cancels `CsFloatScheduler` when ready set empty
- [ ] Steam confirmation flows untouched
- [ ] Patch version bump if shipped alone

### Acceptance (T060)

- [ ] Settings toggle default OFF; OFF cancels WorkManager
- [ ] Worker renews only accounts with usable refresh path; does not change native active account incorrectly
- [ ] Zero Steam Guard getlist traffic from this worker

### Acceptance (T061) — see NOW

- [ ] Confirmation `t`/`k` use Steam time offset shared with code generator
- [ ] Offset cached (no QueryTime per item)
- [ ] Fallback + visible skew hint if align fails

### Acceptance (T012) / (T013) / (T020–T021)

- Unchanged from prior Architect pass — see older checklists in DEV_LOG Cycle 1 if needed; keep PROTOCOL floors.

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
| P0 | T040 | done | Worker | Hub delete-by-substring — `5234051` / 1.5.2 |
| P1 | T041 | todo | — | Pace accept-all / trade auto-confirm |
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
| P0 | T040 | done | Worker | Hub delete fix `5234051` (1.5.2) |
| P0 | T010 | done | Worker | CSFloat Phase-1 scaffold + 1.5.1 (`b3bb468`) |
| P0 | T001 | done | Architect | Review PROTOCOL + CSFLOAT_NOTES; first slice = T010 |
| P1 | T002 | done | Architect | Settings shape in CSFLOAT_NOTES + scaffold |
| P1 | T003 | deferred | — | Fold into T021 after T012 |
| P2 | T004 | done | Architect | Battery/network budget in CSFLOAT_NOTES |

## Boss / Architect notes

- Steam-safety gate: confirmation **timer** polling → reject.
- **Boss Cycle 3:** T040 **APPROVED**. Single NOW = **T061** (Steam-aligned confirmation HMAC → 1.5.3). T011 waits. Next: T041 then T011.
- T012 needs richer trade DTO fields (buyer, price, hash name, assets, steam_offer) — current `CsFloatTradeSummary` is too thin.
- Architect docs-only; Boss owns NOW ordering.