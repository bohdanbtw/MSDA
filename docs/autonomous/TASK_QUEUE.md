# TASK_QUEUE

Prioritized backlog. **Architect** invents / refines, **Boss** orders NOW, **Worker** implements.

Status legend: `todo` | `doing` | `done` | `blocked` | `deferred`

**Boss rule:** at most **1–3** tasks in `doing` / NOW. Worker takes only the NOW block.  
**Architect rule:** docs-only edits; do not fight Worker on Kotlin files.

App HEAD: **1.6.0** (T012 pending sales).

---

## NOW (Worker — start immediately)

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| — | — | idle | — | **Awaiting Boss.** Recommend **T030** (widget copy) or **T013** (CSFloat notify). |

### Acceptance (T012) — done 2026-08-21

- [x] Expanded `CsFloatTradeSummary` + parse (name, price, buyer, asset, offer)
- [x] Pending sales dialog: read-only list + Refresh; empty/401/429/network
- [x] No accept / offer / Guard
- [x] Version **1.6.0** / `160000`

### Acceptance (T060) — **Boss APPROVED** (`2fb1410`, v1.5.6)

- [x] Settings Switch default **OFF**; explains no confirmation polling
- [x] ON → schedule; OFF → cancel; worker early-exits if pref OFF
- [x] `BackgroundSyncScheduler.disable` does **not** cancel renewal
- [x] By-steamId renewal only; zero getlist/confirm from worker
- [x] Version **1.5.6** / `150006`; DEV_LOG + push

---

## NEXT (Boss promote after T012)

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P1 | T013 | todo | — | **CSFloat notification on actionable trades.** Cheap `/me` (or count delta); notify only; tap opens T012 list. No Steam Guard. |
| P1 | T030 | todo | — | **Widget tap-to-copy 2FA** + Toast; fix countdown when bound by `steamId` only (today seconds → `-1`). |
| P1 | T063 | todo | — | **Confirm All type breakdown + trade friction.** Counts by type; trades need extra checkbox (default off). |
| P1 | T065 | todo | — | **Import conflict by SteamID** — Replace / Keep both / Cancel; never silent clobber of different accounts. |
| P1 | T064 | todo | — | **Dual SDA export:** Secrets-only vs Full SessionData chooser. |
| P1 | T020 | todo | — | **Opt-in CSFloat accept + Steam offer send** (second toggle default OFF); enqueue offer id; **no** auto Guard. |
| P1 | T021 | todo | — | **Safe CSFloat Guard confirm** — whitelist only; floors; audit (CSFLOAT_NOTES T021). |
| P1 | T042 | todo | — | **Wire or remove** dead market/gift auto-confirm APIs in `AppSettings` (+ menu strings if orphaned). |
| P2 | T022 | todo | — | Dual-bot conflict banner + sales audit UI. |
| P2 | T062 | todo | — | Encrypt mafile secrets at rest (Keystore); plaintext import migrates; export still SDA-compatible. |
| P2 | T066 | todo | — | Period-aligned 2FA UI tick (Main + widget) — cut 1 Hz AlarmManager wakeups. |
| P2 | T067 | todo | — | Hub multi-select: renew selected + disable auto-confirm panic switch. |
| P2 | T069 | todo | — | **CSFloat Away toggle** (`PATCH /me` `{away}`) in per-account dialog — stall Online/Offline without VPS bot. |
| P2 | T070 | todo | — | Hub row badge when CSFloat enabled for that steamId (icon/dot only). |
| P2 | T061b | todo | — | Tighten `looksLikeClockSkew` (broad `"invalid"` match) — non-blocking follow-up from T061. |

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

### Acceptance (T072)

- [ ] After successful `/me` (Test or Refresh), show balance + pending balance in CSFloat dialog
- [ ] No extra polling; values cleared when key cleared

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
| P0 | T012 | done | Worker | CSFloat read-only pending sales UI (**1.6.0**) |
| P0 | T060 | done | Worker | Opt-in session keep-alive (**1.5.6**) |
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
- **Order:** finish **T012** (**1.5.7**) → **T013** / **T068** / **T072** (CSFloat UX) or **T030** (widget) → only then **T020**.
- Do **not** start T020/T021 until T012 list is usable; dual-bot warning belongs with T022 near T021.
- Architect docs-only; do not fight Worker on Kotlin (T012 WIP in progress).
