# TASK_QUEUE

Prioritized backlog. **Architect** invents / refines, **Boss** orders NOW, **Worker** implements.

Status legend: `todo` | `doing` | `done` | `blocked` | `deferred`

**Boss rule:** at most **1–3** tasks in `doing` / NOW. Worker takes only the NOW block.  
**Architect rule:** docs-only edits; do not fight Worker on Kotlin files.

App HEAD: **1.6.4** (`969e36b` T068+T088). Boss: T068 **APPROVED**. Single NOW = **T065**.

---

## NOW (Worker — start immediately)

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P0 | T065 | doing | Worker | **Import conflict by SteamID (1.6.5).** Detect existing account by steamId (not filename alone); dialog **Replace** / **Keep both** / **Cancel**. Replace → one mafile per steamId + native reload; Keep both → unique filename. Warn same filename + different steamId. No silent clobber. Touch import/Hub paths only. **No CSFloat / Steam getlist / confirm spam.** Bump `1.6.5` / `160005`. DEV_LOG + push. |

### Acceptance (T065) — concrete

- [ ] Detect existing account by **steamId** (not filename alone) before write
- [ ] Dialog: **Replace** / **Keep both** / **Cancel**
- [ ] Replace: one mafile per steamId left on disk + native reload
- [ ] Keep both: unique filename; both appear in Hub
- [ ] Same filename + different steamId: warn before overwrite
- [ ] No silent clobber of a different account’s secrets
- [ ] Version **1.6.5** / `160005`; DEV_LOG + push

### Acceptance (T068) — **Boss APPROVED** (`969e36b`, v1.6.4)

- [x] CSFloat settings dialog shows “Last checked <relative/absolute> · N pending” or Never
- [x] Values from existing prefs; Pending Refresh updates last-check + count (**T088** same ship; no notify from foreground refresh)
- [x] Clear key / disable clears strip; no Steam traffic
- [x] Version **1.6.4** / `160004`; DEV_LOG + push

### Acceptance (T013) — **Boss APPROVED** (`33c10ff`, v1.6.3)

- [x] Count-delta vs last_queued; first run baseline only
- [x] Notify only on increase; never API key in notification
- [x] Tap → Main Pending sales dialog
- [x] Channel `csfloat_sales`; `clearAccount` cancels notification (**T081 satisfied**)
- [x] Zero Steam Guard; Version **1.6.3** / `160003`

### Acceptance (T063) — **Boss APPROVED** (`d91a475`, v1.6.2)

- [x] Accept-all type counts; trades checkbox default OFF; T041 pacing
- [x] Version **1.6.2** / `160002`

### Acceptance (T030) — **Boss APPROVED** (`161dae2`, v1.6.1)

- [x] Widget copy + steamId countdown; Version `1.6.1` / `160001`

### Acceptance (T012) — **Boss APPROVED** (`8b25eb6`, v1.6.0)

- [x] Pending sales read-only; Version **1.6.0** / `160000`

### Acceptance (T060) — **Boss APPROVED** (`2fb1410`, v1.5.6)

- [x] Session keep-alive opt-in; Version **1.5.6** / `150006`

---

## NEXT (after T065)

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P0 | T103 | todo | — | **Tap status strip → Pending sales** (zero extra HTTP). |
| P0 | T077 | todo | — | **POST_NOTIFICATIONS runtime prompt** on CSFloat enable (API 33+); soft-fail if denied. |
| P1 | T072 | todo | — | **CSFloat balance line** from `/me` after Test connection. |
| P1 | T085 | todo | — | **Notify on new trade ids** (not only count↑). |
| P1 | T084 | todo | — | **Per-account notify mute** (default notify ON). |
| P1 | T076 | todo | — | **Cheap `/me` actionable probe** — skip trades list when unchanged. |
| P1 | T079 | todo | — | **Check now** one-shot WorkManager from CSFloat dialog. |
| P1 | T064 | todo | — | **Dual SDA export:** Secrets-only vs Full SessionData. |
| P1 | T031 | todo | — | **Hub search/filter** by name + label. |
| P1 | T042 | todo | — | **Wire or remove** dead market/gift auto-confirm APIs. |
| P1 | T089 | todo | — | CSFloat notification small icon → app launcher icon (replace `ic_dialog_info`). |
| P1 | T090 | todo | — | When enabling CSFloat: one-time dual-bot warning (“don’t run VPS bot on same account”). |
| P1 | T020 | todo | — | **Opt-in CSFloat accept + Steam offer send** (second toggle default OFF); **no** auto Guard. |
| P1 | T021 | todo | — | **Safe CSFloat Guard confirm** — whitelist only; floors; audit. |
| P2 | T022 | todo | — | Dual-bot conflict banner + sales audit UI. |
| P2 | T069 | todo | — | **CSFloat Away toggle**. |
| P2 | T070 | todo | — | Hub CSFloat-enabled badge. |
| P2 | T073 | todo | — | Pending-sales sort + offer-state chip. |
| P2 | T074 | todo | — | Sensitive clipboard flag. |
| P2 | T075 | todo | — | Hub session-expiring → renew. |
| P2 | T078 | todo | — | Widget a11y + haptic on copy. |
| P2 | T080 | todo | — | SaleWorker release log hygiene. |
| P2 | T086 | todo | — | Settings global CSFloat worker status. |
| P2 | T087 | todo | — | Hub filter chips: All / Session / CSFloat. |
| P2 | T050 | todo | — | Clipboard auto-clear 2FA. |
| P2 | T062 | todo | — | Encrypt mafile secrets at rest. |
| P2 | T066 | todo | — | Period-aligned 2FA UI tick. |
| P2 | T067 | todo | — | Hub multi-select renew + panic switch. |
| P2 | T061b | todo | — | Tighten `looksLikeClockSkew`. |

## After T068 / T065 (next wave)

Real-user features once status strip + safe import land. Prefer these over early **T020**.

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P1 | T091 | todo | — | **Hub long-press → copy SteamID** (+ Toast); optional copy account name. |
| P1 | T092 | todo | — | **Offline / session hint on Confirm Load** when network missing or session expired (Renew shortcut). Supersedes thin T071. |
| P1 | T093 | todo | — | **Share 2FA via system share sheet** (Main + Hub) as alt to clipboard-only. |
| P1 | T094 | todo | — | **Import folder batch summary** after multi-mafile pick: N added / N replaced / N skipped + failures. |
| P1 | T095 | todo | — | **Export all accounts ZIP** from Hub (secrets warning → T051); one share Intent. |
| P1 | T096 | todo | — | **Confirmation row relative time** (“2m ago”) + type icon (market/trade/other). |
| P1 | T097 | todo | — | **CSFloat pending empty CTA**: “No sales — Check now” → T079; show last-checked if any. |
| P2 | T098 | todo | — | **Optional biometric gate** on cold start (default OFF); PIN still works if set. |
| P2 | T099 | todo | — | **Widget multi-account flip**: next/prev account buttons on widget (bound list). |
| P2 | T100 | todo | — | **Account archive** (hide from Hub without delete); restore from Settings. |
| P2 | T101 | todo | — | **CSFloat 429 cooloff UX**: dialog/status “Rate limited — retry in Xs” using `Retry-After`. |
| P2 | T102 | todo | — | **Last successful Steam renew timestamp** on Hub row (pairs T075). |

## Post-wave polish — Architect Cycle 9 (**T068 landed**)

Build on status strip + notify. Prefer over early **T020**.

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P1 | T103 | todo | — | **Tap status strip → Pending sales** (same path as button; zero extra HTTP). |
| P1 | T104 | todo | — | **Hub pending-sales badge** from `last_queued_count` (dot/count); tap opens account Main + pending. |
| P1 | T105 | todo | — | **Last CSFloat error chip** on dialog (401 / 429 / network) from worker/Test; cleared on success. |
| P1 | T106 | todo | — | **Long-press pending row → copy trade id** (+ optional buyer SteamID). |
| P1 | T107 | todo | — | **Confirm Load cancelable** (dismiss progress / ignore late result); no duplicate Load spam. |
| P1 | T108 | todo | — | **CSFloat unmetered-only** opt-in constraint on WorkManager (default OFF = any network). |
| P1 | T109 | todo | — | **Launcher shortcut** “Open last account” / dynamic shortcut after Hub open. |
| P1 | T110 | todo | — | **Filter pending sales** by state (`queued`/`pending`) when list &gt; ~8. |
| P2 | T111 | todo | — | **Reset CSFloat notify baseline** button (re-baseline without notify spam) for stuck silence. |
| P2 | T112 | todo | — | **Hub sort modes**: name / recently opened / CSFloat pending count. |
| P2 | T113 | todo | — | **Settings: Steam time offset** readout from `TimeAligner` cache (support / skew debug). |
| P2 | T114 | todo | — | **Decline-all** with same type counts + trade friction as T063 accept-all. |
| P2 | T115 | todo | — | **Import .zip of mafiles** (unzip to temp → existing import path + T065 conflicts). |

## Cycle 10 — more daily-driver value

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P1 | T116 | todo | — | **Cache last `/me` balance** on disk so dialog shows last-known until next Test/Check-now. |
| P1 | T117 | todo | — | After notif denied: button **Open system notification settings** for MSDA. |
| P1 | T118 | todo | — | **Pending sales footer: sum of prices** (visible rows / all loaded). |
| P1 | T119 | todo | — | **CSFloat interval chips** 15 / 30 / 60 / 120 min (still clamp 15–240). |
| P1 | T120 | todo | — | **Confirmation detail sheet** before accept (type, age, creator id); Accept/Decline there. |
| P1 | T121 | todo | — | **Import mafile from clipboard** (paste JSON → same T065 conflict path). |
| P1 | T122 | todo | — | **Warn duplicate `identity_secret`** across two Hub accounts (compromise/clone risk). |
| P1 | T123 | todo | — | **Panic switch**: one Settings action disables CSFloat + session renewal + market/gift auto-confirm flags. |
| P2 | T124 | todo | — | Hub **pull-to-refresh** account list + codes. |
| P2 | T125 | todo | — | Highlight **last-opened account** in Hub (subtle). |
| P2 | T126 | todo | — | Haptic on successful confirm accept/decline (optional, default on). |
| P2 | T127 | todo | — | Widget shows **account label** preference (name / label / both). |
| P2 | T128 | todo | — | Persist CSFloat Test result timestamp next to balance (“tested 3m ago”). |

### T088 — **done** inside T068 (`969e36b`)

Pending Refresh syncs `setLastQueuedCount` without notification; strip refreshes.

### Acceptance (T077) / (T072) / (T079)

- T077: request permission on enable (API 33+); denied = silent poll OK
- T072: show balance + pending after successful `/me` Test; clear with key
- T079: Check-now one-shot unique work; disable button while running

### Acceptance (T076) / (T084) / (T085) / (T089) / (T090)

- T076: parse actionable hint; skip trades when unchanged; fallback if missing
- T084: mute pref; OFF suppresses notify only
- T085: capped last-seen trade id set; alert on new ids
- T089: use app icon / mipmap for notification small icon
- T090: one-time dialog/copy on first enable; link dual-bot note; don’t block enable

### Acceptance (T065) / (T073)

- T073: sort state then price; offer-state when non-blank; read-only
- T065: see concrete checklist above

### Acceptance (T091–T097)

- T091: long-press Hub row copies steamId; does not open account accidentally
- T092: Load empty/error distinguishes offline vs needs-login; Renew visible when session bad
- T093: share sheet sends code text only; no mafile secrets
- T094: post-import snackbar/dialog with counts; failures listed briefly
- T095: Hub action builds zip of all mafiles; warns before share; no passwords in zip unless already in mafile
- T096: each confirmation shows relative time + type; Load path unchanged otherwise
- T097: empty pending sales shows last-checked + Check-now button (needs T079 or inline refresh)

### Acceptance (T098–T102)

- T098: default OFF; uses BiometricPrompt; failure falls back to existing PIN/none
- T099: widget next/prev cycles configured accounts without opening configure
- T100: archived accounts hidden from Hub list; data retained; restore undoes hide
- T101: surface Retry-After from CSFloat 429 on dialog/status; no tight retry loop
- T102: Hub shows compact “renewed …” when known; blank if never

### Acceptance (T103–T110)

- T103: strip clickable when key present; opens pending dialog; no auto network
- T104: Hub shows count only when CSFloat enabled + count&gt;0; tap navigates correctly
- T105: stores last error code/string safely (no API key / body); success clears
- T106: long-press copies id; short tap unchanged (read-only)
- T107: second Load while in-flight ignored or cancels prior; UI never applies stale list after cancel
- T108: Settings/CSFloat toggle; when ON, periodic work requires unmetered; floor interval unchanged
- T109: shortcut opens correct account Main; removed when account deleted
- T110: chip/filter queued|pending|all; empty filter message clear

### Acceptance (T111–T115)

- T111: reset clears baseline + last count; next worker run baselines only (no notify)
- T112: sort persists; default name; CSFloat sort uses last_queued prefs only
- T113: read-only offset seconds + “last aligned”; no new Steam calls from this screen
- T114: decline-all mirrors T063 friction + T041 pacing; single decline unchanged
- T115: zip import uses SAF; applies T065 per entry; temp cleaned

### Acceptance (T116–T123)

- T116: show cached balance when offline/untested; label “as of …”; clear with key
- T117: deep-link to app notification settings; no crash if Activity not found
- T118: footer uses priceCents sum; “—” if all unknown; updates with filter (T110)
- T119: chips set interval field; custom still allowed; persists per steamId
- T120: sheet is optional path; list accept still works; no getlist spam
- T121: invalid JSON → clear error; valid → T065 flow
- T122: warn only (don’t block); never show full secret
- T123: one tap + confirm dialog; cancels CSFloat WM + session renewal; leaves mafiles intact

### Acceptance (T124–T128)

- T124: refresh does not flip active account incorrectly
- T125: highlight clears when another account opened
- T126: respects system haptic settings
- T127: configure screen choice; default current behavior
- T128: updates only on successful `/me`; independent of sale worker last-check

### Acceptance (T020–T021)

- CSFLOAT_NOTES Phase 4–5. **Gate:** T013 live (**done**) + dual-bot warning (**T090**/T022) before auto-accept.

---

## UI polish (real-user value)

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P1 | T031 | todo | — | Hub search/filter. |
| P1 | T034 | todo | — | Code monospace + letter-spacing; accurate progress. |
| P2 | T032 | todo | — | Session health chip. |
| P2 | T033 | todo | — | Material confirmation rows + Coil/Glide. |
| P2 | T035 | todo | — | Settings Preference-style sections. |
| P2 | T070 | todo | — | Hub CSFloat badge. |
| P2 | T073 | todo | — | Pending-sales sort + offer chip. |
| P2 | T078 | todo | — | Widget copy a11y + haptic. |
| P1 | T092 | todo | — | Offline / session hint on Confirm Load (+ Renew). |
| P3 | T071 | todo | — | Folded into **T092** (keep id only if Boss prefers split). |

---

## Bugs / tech debt

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P1 | T042 | todo | — | Wire or remove market/gift auto-confirm dead API |
| P1 | T061b | todo | — | Tighten `looksLikeClockSkew` |
| P2 | T043 | todo | — | PasswordManager case unify |
| P2 | T044 | todo | — | Quarantine dead confirmation background leftovers |
| P2 | T045 | todo | — | Proxy passwords → Keystore |
| P2 | T080 | todo | — | SaleWorker release log hygiene |
| P3 | T046 | todo | — | Fix/delete orphaned `PasswordBackupHelper` |

---

## Smaller authenticator features

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P2 | T050 | todo | — | Clipboard auto-clear 2FA (30–60s). |
| P2 | T051 | todo | — | Export mafile secrets warning. |
| P2 | T074 | todo | — | Sensitive clipboard flag. |
| P2 | T091 | todo | — | Hub long-press copy SteamID. |
| P2 | T093 | todo | — | Share 2FA via share sheet. |
| P2 | T096 | todo | — | Confirmation relative time + type icon. |
| P3 | T052 | todo | — | PIN attempt lockout / backoff. |

---

## Done / deferred

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P0 | T068 | done | Worker | CSFloat status strip (**1.6.4**) |
| P1 | T088 | done | Worker | Pending Refresh syncs last_* (inside T068) |
| P0 | T013 | done | Worker | CSFloat count-delta sale notifications (**1.6.3**) |
| P1 | T081 | done | Worker | Cancel notif on clearAccount (shipped inside T013) |
| P1 | T063 | done | Worker | Confirm-all type breakdown + trade opt-in (**1.6.2**) |
| P0 | T030 | done | Worker | Widget tap-to-copy + steamId countdown (**1.6.1**) |
| P0 | T012 | done | Worker | CSFloat read-only pending sales (**1.6.0**) |
| P0 | T060 | done | Worker | Opt-in session keep-alive (**1.5.6**) |
| P0 | T011 | done | Worker | CSFloat Test connection (**1.5.5**) |
| P1 | T041 | done | Worker | Pace accept-all / trade auto-confirm (**1.5.4**) |
| P0 | T061 | done | Worker | Steam-aligned confirmation HMAC (**1.5.3**) |
| P0 | T040 | done | Worker | Hub delete-by-substring → **1.5.2** |
| P0 | T010 | done | Worker | CSFloat Phase-1 scaffold + **1.5.1** |
| P0 | T001–T004 | done/deferred | Architect | Protocol / CSFloat notes bootstrap |

## Boss / Architect notes

- Steam-safety gate: confirmation **timer** polling → reject.
- **NOW = T065** (Boss). After: **T103** then **T077/T072** (or Worker may finish T077 WIP if Boss allows) → **T116+** / **T091+** → **T090** before **T020**.
- Race note: local uncommitted Kotlin looked like **T077+T072** while NOW is **T065** — Worker should follow Boss NOW (stash CSFloat WIP). Architect does not touch Kotlin / watchers.
- Cycle 10 seeded **T116–T128**.
