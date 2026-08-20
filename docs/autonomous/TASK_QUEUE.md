# TASK_QUEUE

Prioritized backlog. **Architect** invents / refines, **Boss** orders NOW, **Worker** implements.

Status legend: `todo` | `doing` | `done` | `blocked` | `deferred`

**Boss rule:** at most **1–3** tasks in `doing` / NOW. Worker takes only the NOW block.  
**Architect rule:** docs-only edits; do not fight Worker on Kotlin files.

App HEAD: **1.5.6** (T060 session renewal).

---

## NOW (Worker — start immediately)

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P0 | T012 | todo | Worker | **CSFloat pending sales (foreground, read-only).** Expand DTO → list UI → manual Refresh. No accept / offer / Guard. Bump **1.5.7**. |

### Acceptance (T060) — done 2026-08-21

- [x] Settings Switch default **OFF**; explains no confirmation polling
- [x] ON → schedule; OFF → cancel; worker early-exits if pref OFF
- [x] `BackgroundSyncScheduler.disable` does **not** cancel renewal
- [x] By-steamId renewal only; zero getlist/confirm from worker
- [x] Version **1.5.6** / `150006`; DEV_LOG + push

---

## NEXT (Boss promote after T012)

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P0 | T012 | todo | — | **CSFloat pending sales (foreground, read-only).** Expand DTO → list UI → manual Refresh. No accept / offer / Guard. |
| P1 | T013 | todo | — | **CSFloat notification on actionable trades.** Cheap `/me` (or count delta); notify only; tap opens T012 list. No Steam Guard. |
| P1 | T030 | todo | — | **Widget tap-to-copy 2FA** + Toast; fix countdown when bound by `steamId` only (today seconds → `-1`). |
| P1 | T063 | todo | — | **Confirm All type breakdown + trade friction.** Counts by type; trades need extra checkbox (default off). |
| P1 | T065 | todo | — | **Import conflict by SteamID** — Replace / Keep both / Cancel; never silent clobber of different accounts. |
| P1 | T064 | todo | — | **Dual SDA export:** Secrets-only vs Full SessionData chooser. |
| P1 | T020 | todo | — | **Opt-in CSFloat accept + Steam offer send** (second toggle default OFF); enqueue offer id; **no** auto Guard. |
| P1 | T021 | todo | — | **Safe CSFloat Guard confirm** — whitelist only; floors; audit (CSFLOAT_NOTES T021). |
| P1 | T042 | todo | — | **Wire or remove** dead market/gift auto-confirm APIs in `AppSettings` (+ menu strings if orphaned). |
| P1 | T068 | todo | — | **CSFloat status strip:** last-checked time + queued count on Main CSFloat dialog (from prefs written by worker/T012). |
| P2 | T022 | todo | — | Dual-bot conflict banner + sales audit UI. |
| P2 | T062 | todo | — | Encrypt mafile secrets at rest (Keystore); plaintext import migrates; export still SDA-compatible. |
| P2 | T066 | todo | — | Period-aligned 2FA UI tick (Main + widget) — cut 1 Hz AlarmManager wakeups. |
| P2 | T067 | todo | — | Hub multi-select: renew selected + disable auto-confirm panic switch. |
| P2 | T069 | todo | — | **CSFloat Away toggle** (`PATCH /me` `{away}`) in per-account dialog — stall Online/Offline without VPS bot. |
| P2 | T070 | todo | — | Hub row badge when CSFloat enabled for that steamId (icon/dot only). |
| P2 | T061b | todo | — | Tighten `looksLikeClockSkew` (broad `"invalid"` match) — non-blocking follow-up from T061. |

### Acceptance (T012) — concrete

- [ ] Expand `CsFloatTradeSummary` (+ parse in `listQueuedTrades`) per CSFLOAT_NOTES: at least `marketHashName`, `priceCents`, `buyerSteamId`, `state`, `steamOfferId`/`steamOfferState`, `assetId`
- [ ] Main (or dedicated sheet) shows **queued/pending** rows for the **active** account only when CSFloat enabled + key present
- [ ] Each row: item name, price (USD from cents), state chip, optional buyer id truncated
- [ ] **Refresh** button only (no timer, no WorkManager change required for UI path)
- [ ] Empty / 401 / 429 / network states with clear copy (reuse T011 patterns; never log key)
- [ ] **No** `POST /trades/*/accept`, no Steam offer send, no getlist/confirm
- [ ] Minor version bump when shipped (e.g. **1.6.0** if treated as feature; else patch if Boss prefers)

### Acceptance (T013)

- [ ] `CsFloatSaleWorker` (or one-shot) compares actionable/queued count vs last-seen; posts notification only on **increase**
- [ ] Notification tap → open account Main / sales list (T012)
- [ ] Channel: low-importance default; user can disable OS notifications without clearing API key
- [ ] Still **zero** Steam Guard traffic; honor 429 cooloff; interval floor unchanged (≥15m)

### Acceptance (T030)

- [ ] Tap widget **code** (or dedicated copy affordance) copies 2FA; Toast “Copied”
- [ ] Long-press / account title still opens configure (don’t lose rebind)
- [ ] Countdown works when widget bound by **steamId** (add `getSecondsToNextCodeForSteamId` JNI **or** compute from period math without mutating active account)
- [ ] No 1 Hz regression beyond current widget tick (T066 can fix cadence later)

### Acceptance (T063)

- [ ] Accept-all dialog lists counts: market / trade / other (or Steam type ids mapped to labels)
- [ ] Including trades requires explicit checkbox (**default unchecked**)
- [ ] Single-item accept/decline UX unchanged; keep T041 pacing on multi-accept

### Acceptance (T065)

- [ ] Same `steamId` import → Replace / Keep both / Cancel
- [ ] Replace leaves one mafile per steamId
- [ ] Different steamId + same filename → warn before overwrite

### Acceptance (T068)

- [ ] Persist `last_checked_ms_<steamId>` + `last_queued_count_<steamId>` from worker and/or T012 refresh
- [ ] CSFloat dialog shows “Last checked … · N pending” (or “Never”)
- [ ] Clearing key / disable clears those prefs

### Acceptance (T069)

- [ ] Toggle Away with confirmation; calls `PATCH /me`; reflects current away if `/me` exposes it
- [ ] Failures show 401/429/network; no Steam calls

### Acceptance (T020–T021)

- Unchanged safety floors — see CSFLOAT_NOTES Phase 4–5 and T021 sketch. Second toggle default OFF; whitelist Guard only.

---

## UI polish (real-user value)

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P1 | T030 | todo | — | Widget copy + steamId countdown fix (see above). |
| P1 | T031 | todo | — | **Hub search/filter** by account name + label (post–T040 delete). |
| P1 | T034 | todo | — | Code display monospace + letter-spacing; progress bar matches real seconds-left. |
| P2 | T032 | todo | — | **Session health chip** on Hub/Main (valid / expiring / needs login). |
| P2 | T033 | todo | — | Material confirmation rows + Coil/Glide (cancel on detach). |
| P2 | T035 | todo | — | Settings Preference-style sections (Account / CSFloat / Security / About). |
| P2 | T070 | todo | — | Hub CSFloat-enabled badge. |
| P3 | T071 | todo | — | Confirmation Load empty-state: short “why empty / session?” hint + Renew shortcut. |

### Acceptance (T031)

- [ ] Filter box filters Hub list by name and custom label (case-insensitive)
- [ ] Empty filter shows all; deleting account while filtered still uses steamId-safe delete (T040)

### Acceptance (T034)

- [ ] 2FA digits use monospace / tabular figures; spacing readable at a glance
- [ ] Progress/timer never jumps backward within a period except at rollover

---

## Bugs / tech debt

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P1 | T042 | todo | — | Wire or remove market/gift auto-confirm dead API |
| P1 | T061b | todo | — | Tighten `looksLikeClockSkew` heuristic |
| P2 | T043 | todo | — | PasswordManager: unify `hasPassword` / `getPassword` case handling |
| P2 | T044 | todo | — | Quarantine/remove dead `BackgroundSyncScheduler` leftover names / `ConfirmationBackgroundWorker` after T060 lands cleanly |
| P2 | T045 | todo | — | Proxy passwords → Keystore store |
| P3 | T046 | todo | — | Fix/delete orphaned `PasswordBackupHelper` |

---

## Smaller authenticator features

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P2 | T050 | todo | — | Clipboard auto-clear 2FA after copy (30–60s) — Hub + Main + widget. |
| P2 | T051 | todo | — | Export mafile secrets warning dialog (identity_secret visible risk). |
| P3 | T052 | todo | — | PIN attempt lockout / backoff. |

### Acceptance (T050)

- [ ] After copy, schedule clear of clipboard if still MSDA’s 2FA clip; don’t clear unrelated clipboard content
- [ ] Works on API 28+ (`clearPrimaryClip` / sensitive flag where available)

---

## Done / deferred

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P0 | T011 | done | Worker | CSFloat Test connection + clear key (**1.5.5**) |
| P1 | T041 | done | Worker | Pace accept-all / trade auto-confirm (**1.5.4**) |
| P0 | T061 | done | Worker | Steam-aligned confirmation HMAC (**1.5.3**); follow-up → T061b |
| P0 | T040 | done | Worker | Hub delete-by-substring → **1.5.2** |
| P0 | T010 | done | Worker | CSFloat Phase-1 scaffold + **1.5.1** |
| P0 | T001 | done | Architect | Review PROTOCOL + CSFLOAT_NOTES |
| P1 | T002 | done | Architect | Settings shape in CSFLOAT_NOTES |
| P1 | T003 | deferred | — | Fold into T021 after T012 |
| P2 | T004 | done | Architect | Battery/network budget in CSFLOAT_NOTES |

## Boss / Architect notes

- Steam-safety gate: confirmation **timer** polling → reject.
- **Recommend order:** finish **T060** (1.5.6) → **T012** (read-only sales, feature bump) → **T030** or **T013** in parallel preference (widget vs notify).
- Do **not** start T020/T021 until T012 + T013 UX is usable and dual-bot warning is documented in UI (T022 can follow T021).
- Architect docs-only; Worker owns Kotlin. Observed dirty T060 files — Architect will not touch them.
