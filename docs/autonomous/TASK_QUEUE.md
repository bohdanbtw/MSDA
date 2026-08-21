# TASK_QUEUE

Prioritized backlog. **Architect** invents / refines, **Boss** orders NOW, **Worker** implements.

Status legend: `todo` | `doing` | `done` | `blocked` | `deferred`

**Boss rule:** at most **1–3** tasks in `doing` / NOW. Worker takes only the NOW block.  
**Architect rule:** docs-only edits; do not fight Worker on Kotlin files.

App HEAD: **1.6.21** (T142 swipe-to-refresh pending sales). Queue idle — autonomous cycle stopped by user after T106+T142.

---

## NOW (Worker — start immediately)

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| — | — | idle | — | **Stopped.** Autonomous cycle ended by user after T106+T142. Do not start further features. |

### Acceptance (T142) — done 2026-08-21 (v1.6.21)

- [x] Pull-to-refresh triggers same load path as Refresh button
- [x] No notification side effects; zero Steam Guard / getlist
- [x] Version **1.6.21** / `160021`; sole-Gradle; DEV_LOG + push

### Acceptance (T106) — **Boss APPROVED** (`5625b74`, v1.6.20)

- [x] Long-press pending row copies trade id (+ Toast); short tap unchanged
- [x] Zero Steam Guard / getlist; Version **1.6.20** / `160020`; sole-Gradle; DEV_LOG + push

### Acceptance (T104) — **Boss APPROVED** (`a2a3e4a`, v1.6.19)

- [x] Hub shows pending count only when CSFloat enabled + count&gt;0
- [x] Tap navigates to account Main + pending; zero Steam Guard / getlist
- [x] Version **1.6.19** / `160019`; sole-Gradle; DEV_LOG + push

### Acceptance (T096) — **Boss APPROVED** (`2476d14`, v1.6.18)

- [x] Each confirmation row shows relative time + type icon (market/trade/other)
- [x] Load path unchanged otherwise; no confirmation timer polling / getlist spam
- [x] Version **1.6.18** / `160018`; sole-Gradle; DEV_LOG + push

### Acceptance (T092) — **Boss APPROVED** (`73c14fb`, v1.6.17)

- [x] Load empty/error distinguishes offline vs needs-login; Renew visible when session bad
- [x] No confirmation timer polling / getlist spam
- [x] Version **1.6.17** / `160017`; sole-Gradle; DEV_LOG + push

### Acceptance (T076) — **Boss APPROVED** (`7edbcd2`, v1.6.16)

- [x] Parse actionable/queued hint from `/me` into `CsFloatMeSummary` when present
- [x] If hint unchanged vs last stored + baseline exists → **skip** `listQueuedTrades`
- [x] If hint missing/unparseable → fall back to current trades-list behavior
- [x] Still write last-checked; notify rules (T085/T084) unchanged when trades fetched
- [x] Zero Steam Guard; Version **1.6.16** / `160016`; sole-Gradle; DEV_LOG + push

### Acceptance (T097) — **Boss APPROVED** (`966dc62`, v1.6.15)

- [x] Empty pending sales shows last-checked (or Never) + Check-now affordance (T079 path)
- [x] Non-empty list unchanged; zero Steam Guard / getlist
- [x] Version **1.6.15** / `160015`; sole-Gradle; DEV_LOG + push

### Acceptance (T091) — **Boss APPROVED** (`5d8f319`, v1.6.14)

- [x] Long-press Hub row copies steamId + Toast; does not open account
- [x] Zero Steam Guard / getlist; Version **1.6.14** / `160014`; sole-Gradle; DEV_LOG + push

### Acceptance (T084) — **Boss APPROVED** (`fad5000`, v1.6.13)

- [x] Per-account notify toggle default ON; OFF suppresses notify only (poll still runs)
- [x] Zero Steam Guard / getlist; Version **1.6.13** / `160013`; sole-Gradle; DEV_LOG + push

### Acceptance (T085) — **Boss APPROVED** (`e967789`, v1.6.12)

- [x] Persist capped last-seen trade id set per steamId
- [x] Notify when new trade ids appear (not only count increase)
- [x] First run / baseline: no spam; honor 429; never API key in notification
- [x] Zero Steam Guard / getlist; Version **1.6.12** / `160012`; sole-Gradle; DEV_LOG + push

### Acceptance (T129) — **Boss APPROVED** (`00cb213`, v1.6.11)

- [x] Hub row shows steamId as secondary text (truncated OK; full via existing long-press if present)
- [x] Keep-both duplicates no longer look identical in the list
- [x] Zero Steam Guard / getlist / new CSFloat HTTP
- [x] Version **1.6.11** / `160011`; sole-Gradle; DEV_LOG + push

### Acceptance (T141) — **Boss APPROVED** (`2d9ae51`, v1.6.10)

- [x] Status strip uses accent/bold when pending count N&gt;0; still tappable (T103)
- [x] Visual only; no extra HTTP / Steam Guard / getlist
- [x] Version **1.6.10** / `160010`; DEV_LOG + push
- [x] Sole-Gradle respected (single assembleDebug)

### Acceptance (T090) — **Boss APPROVED** (`5eb853a`, v1.6.9)

- [x] On first enable (never-seen), show dual-bot warning dialog/copy
- [x] User can dismiss and still enable; per-steamId pref records seen
- [x] Never logs API key; zero Steam Guard / getlist
- [x] Version **1.6.9** / `160009`; DEV_LOG + push

### Acceptance (T079+T089) — **Boss APPROVED** (`6e71420`, v1.6.8)

- [x] “Check now” enqueues unique one-time CSFloat work (same SaleWorker path)
- [x] Button disabled while running; re-enables on finish; strip refresh after
- [x] Same notify/baseline rules as periodic worker; honor 429
- [x] T089 same ship: notification small icon → `R.mipmap.ic_launcher`
- [x] Zero Steam Guard / getlist; Version **1.6.8** / `160008`; DEV_LOG + push

### Acceptance (T077+T072) — **Boss APPROVED** (`8beb19a`, v1.6.7)

- [x] T077: request permission on enable (API 33+); denied = silent poll OK
- [x] T072: balance + pending after successful `/me` Test; clear with key
- [x] Zero Steam Guard/getlist; Version **1.6.7** / `160007`

### Acceptance (T103) — **Boss APPROVED** (`89f212f`, v1.6.6)

- [x] Strip clickable when key present; opens pending dialog; no auto network beyond existing pending load
- [x] Zero Steam Guard/getlist; Version **1.6.6** / `160006`

### Acceptance (T065) — **Boss APPROVED** (`db63077`, v1.6.5)

- [x] Detect existing account by **steamId** (not filename alone) before write
- [x] Dialog: **Replace** / **Keep both** / **Cancel**
- [x] Replace: one mafile per steamId left on disk + native reload (no clobber of other SteamIDs)
- [x] Keep both: unique filename; both appear in Hub
- [x] Same filename + different steamId: warn before overwrite
- [x] No silent clobber of a different account’s secrets
- [x] Version **1.6.5** / `160005`; DEV_LOG + push

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

## NEXT (after T142)

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P0 | T076 | done | Worker | **Cheap `/me` actionable probe** → **1.6.16**. |
| P0 | T092 | done | Worker | **Offline / session hint on Confirm Load** → **1.6.17**. |
| P1 | T096 | done | Worker | **Confirmation row relative time** + type icon → **1.6.18**. |
| P1 | T104 | done | Worker | **Hub pending-sales badge** from `last_queued_count` → **1.6.19**. |
| P1 | T106 | done | Worker | **Long-press pending row → copy trade id** → **1.6.20**. |
| P1 | T142 | done | Worker | **Swipe-to-refresh** pending sales (no notify) → **1.6.21**. |
| P1 | T101 | todo | — | **CSFloat 429 cooloff UX** (`Retry-After`). |
| P1 | T031 | todo | — | **Hub search/filter** by name + label. |
| P1 | T064 | todo | — | **Dual SDA export:** Secrets-only vs Full SessionData. |
| P1 | T116 | todo | — | **Cache last `/me` balance** between Tests. |
| P1 | T050 | todo | — | Clipboard auto-clear 2FA (30–60s). |
| P1 | T042 | todo | — | **Wire or remove** dead market/gift auto-confirm APIs. |
| P1 | T020 | todo | — | **Opt-in CSFloat accept + Steam offer** (2nd toggle default OFF); **no** auto Guard. |
| P1 | T021 | todo | — | **Safe CSFloat Guard confirm** — whitelist only. |
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
| P2 | T062 | todo | — | Encrypt mafile secrets at rest. |
| P2 | T066 | todo | — | Period-aligned 2FA UI tick. |
| P2 | T067 | todo | — | Hub multi-select renew + panic switch. |
| P2 | T061b | todo | — | Tighten `looksLikeClockSkew`. |

### Acceptance (T076) — concrete (NOW)

- See NOW block checklist (same criteria).

## Cycle 13 — after T076 / T097 (CSFloat UX mature)

Notify + mute + Check-now + strip + empty CTA are live or in-flight. Next user value:

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P1 | T153 | todo | — | **Empty CTA also shows last error** (401/429/network) if set — pairs T105; Check-now still primary. |
| P1 | T154 | todo | — | **Hub badge respects mute**: muted accounts show quiet icon / no badge pulse. |
| P1 | T155 | todo | — | **Pending list: pull = Refresh; toolbar Check-now** stays distinct (Refresh = list only; Check-now = worker path). Document in UI hint. |
| P1 | T156 | todo | — | **Local sales activity log** (last ~20 notify/check events per steamId) read-only in CSFloat dialog. No secrets. |
| P1 | T157 | todo | — | **Quiet hours** per account (e.g. 23:00–08:00 local): poll OK, suppress notify. Default OFF. |
| P1 | T158 | todo | — | **Hub double-tap row → copy 2FA** + Toast (tap still opens; long-press = SteamID). |
| P1 | T159 | todo | — | **Clock-skew banner** when `TimeAligner` abs(offset) &gt; 30s — Renew/align hint; no getlist timer. |
| P1 | T160 | todo | — | **Pending loading skeleton** (not blank flash) while Refresh/Check-now in flight. |
| P2 | T161 | todo | — | **Stale confirmation greying** after successful respond (until next Load). |
| P2 | T162 | todo | — | **Export confirmations snapshot** as plain text (type/age/creator) for support — no secrets. |
| P2 | T163 | todo | — | **In-app What’s new** sheet once per versionCode (from short DEV_LOG bullets). |
| P2 | T164 | todo | — | **CSFloat dialog: last Check-now result** line (“Checked just now · N pending / rate limited”). |
| P2 | T165 | todo | — | **Deep link** `msda://account/{steamId}/csfloat` open pending (for future notif extras). |

## Cycle 14 — keep inventing (NOW stays T097)

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P1 | T166 | todo | — | **Pending row: buyer avatar optional** (Coil; cancel on detach) — or initials placeholder if no URL. |
| P1 | T167 | todo | — | **Main code tap zone larger** (min 48dp) + optional second tap copies again without toast spam. |
| P1 | T168 | todo | — | **Hub section headers** when sorted by CSFloat-on vs others (pairs T112/T087). |
| P1 | T169 | todo | — | **Confirm Load: show count** “12 confirmations” in toolbar after success. |
| P1 | T170 | todo | — | **Per-account proxy indicator** on Hub if proxy configured (no password shown). |
| P1 | T171 | todo | — | **CSFloat: copy API key fingerprint** (last 4 chars only) for support — never full key. |
| P1 | T172 | todo | — | **Failed Check-now → snackbar with Retry** (re-enqueue one-shot). |
| P1 | T173 | todo | — | **Widget: show pending CSFloat count** tiny badge when account opted-in + count&gt;0 (prefs only). |
| P2 | T174 | todo | — | **Auto-dismiss Toast** duration preference (short/long) for copy actions. |
| P2 | T175 | todo | — | **Import: drag-and-drop .mafile** onto Hub (Android 7+ / desktop-like). |
| P2 | T176 | todo | — | **Settings: clear all CSFloat data** (keys+prefs+baselines) with typed confirm. |
| P2 | T177 | todo | — | **Confirmation accept success checkmark** animation (no extra Steam calls). |
| P2 | T178 | todo | — | **Account notes** free-text field (local prefs only; not in mafile export by default). |

### Acceptance (T166–T173)

- T166: no crash if image fails; recycle/cancel loads; optional OFF in settings later
- T167: meets a11y touch target; rapid taps don’t stack Toasts
- T168: headers only when that sort/filter active; empty sections hidden
- T169: count matches list size; clears on account switch
- T170: icon/dot only; tap opens existing proxy UI if any
- T171: fingerprint from stored key length/hash; never logs full key
- T172: Retry uses same T079 unique work; disabled while running
- T173: prefs-only; no CSFloat HTTP from widget tick; updates on existing widget refresh

### Acceptance (T174–T178)

- T174: default = platform Toast length
- T175: same T065 conflict path as SAF import
- T176: wipes secure store + ui prefs for all or current account (clarify in UI)
- T177: animation ≤400ms; doesn’t block next action
- T178: max length ~200; excluded from SDA export unless user opts in later

### Acceptance (T153–T160)

- T153: error text never includes API key/body; cleared on success
- T154: mute OFF → existing badge behavior; ON → muted affordance only
- T155: copy clarifies Refresh vs Check-now; no double notify from Refresh
- T156: ring buffer; clear with account/CSFloat clear; never stores keys
- T157: timezone = device default; crossing midnight OK; poll still runs
- T158: double-tap threshold ~300ms; doesn’t break accessibility long-press
- T159: banner dismissible; uses cached offset only
- T160: cancel/replace in-flight loads don’t leave skeleton stuck

### Acceptance (T161–T165)

- T161: visual only until next Load; no extra Steam calls
- T162: share sheet; user-initiated only
- T163: show once per versionCode; Skip works
- T164: updates from Check-now / worker completion prefs
- T165: invalid steamId → Hub; no auto network beyond existing open-pending

---

## After T068 / T065 (next wave)

Real-user features once status strip + safe import land. Prefer these over early **T020**.

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P1 | T091 | done | Worker | **Hub long-press → copy SteamID** (+ Toast); optional copy account name. |
| P1 | T092 | done | Worker | **Offline / session hint on Confirm Load** → **1.6.17**. Supersedes thin T071. |
| P1 | T093 | todo | — | **Share 2FA via system share sheet** (Main + Hub) as alt to clipboard-only. |
| P1 | T094 | todo | — | **Import folder batch summary** after multi-mafile pick: N added / N replaced / N skipped + failures. |
| P1 | T095 | todo | — | **Export all accounts ZIP** from Hub (secrets warning → T051); one share Intent. |
| P1 | T096 | doing | Worker | **Confirmation row relative time** (“2m ago”) + type icon (market/trade/other) → **1.6.18** (NOW). |
| P1 | T097 | done | Worker | **CSFloat pending empty CTA** → **1.6.15**. |
| P2 | T098 | todo | — | **Optional biometric gate** on cold start (default OFF); PIN still works if set. |
| P2 | T099 | todo | — | **Widget multi-account flip**: next/prev account buttons on widget (bound list). |
| P2 | T100 | todo | — | **Account archive** (hide from Hub without delete); restore from Settings. |
| P2 | T101 | todo | — | **CSFloat 429 cooloff UX**: dialog/status “Rate limited — retry in Xs” using `Retry-After`. |
| P2 | T102 | todo | — | **Last successful Steam renew timestamp** on Hub row (pairs T075). |

## Post-wave polish — Architect Cycle 9 (**T068 landed**)

Build on status strip + notify. Prefer over early **T020**.

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P1 | T104 | doing | Worker | **Hub pending-sales badge** from `last_queued_count` (dot/count); tap opens account Main + pending → **1.6.19** (NOW). |
| P1 | T105 | todo | — | **Last CSFloat error chip** on dialog (401 / 429 / network) from worker/Test; cleared on success. |
| P1 | T106 | done | Worker | **Long-press pending row → copy trade id** (+ optional buyer SteamID) → **1.6.20**. |
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

## Cycle 11 — after T065 import safety

Invented from T065 WIP (`ConflictChoice`, same-SteamID / filename-clash paths). Ship after **1.6.5**.

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P0 | T129 | done | Worker | **Hub secondary line = SteamID** (truncated or full on long-press already T091) so Keep-both clones are distinguishable. |
| P1 | T130 | todo | — | **Batch import “Apply to all”** for same conflict type in one multi-file pick (Replace all / Keep all / Ask each). |
| P1 | T131 | todo | — | **Import outcome snackbar**: Added / Replaced / Kept both / Skipped / Failed counts (pairs T094). |
| P1 | T132 | todo | — | **Reject mafile missing `shared_secret`** (or unusable Guard) with clear dialog before write. |
| P1 | T133 | todo | — | **Rename account file** from Hub (unique name + reload); no steamId change. |
| P1 | T134 | todo | — | **Import preview sheet**: list incoming steamIds + conflict status before any write. |
| P1 | T135 | todo | — | On **Replace**: migrate Hub label / widget bindings / CSFloat prefs to surviving file (same steamId — verify no orphan prefs). |
| P1 | T136 | todo | — | **Account inventory export** (CSV/text): filename, steamId, hasSession, CSFloat on — no secrets. |
| P2 | T137 | todo | — | Prefer `SessionData.AccountName` / `account_name` for Hub title on fresh import. |
| P2 | T138 | todo | — | After Keep-both: offer **set label** dialog (“alt”, “backup”) immediately. |
| P2 | T139 | todo | — | SAF **multi-select** import with progress (“3 of 10”) using T065 per file. |
| P2 | T140 | todo | — | Detect **orphan SessionStore** entries after Replace/delete; cleanup helper. |

## Cycle 12 — T065→T103 path and beyond

Do **not** steal NOW. After **T065** lands, Boss next is **T103**; these fill gaps around that path.

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P0 | T141 | done | Worker | **T103+**: status strip accent when N&gt;0 (**1.6.10**). |
| P1 | T142 | done | Worker | **Swipe-to-refresh** on Pending sales list (same as Refresh button; T088 rules — no notify) → **1.6.21**. |
| P1 | T143 | todo | — | **Confirm Load shows account name** in progress/empty so multi-account users know which Guard list. |
| P1 | T144 | todo | — | **Copy Steam Guard code from confirmation row?** No — instead **show matching 2FA** sticky while confirm list open (auto-updates). |
| P1 | T145 | todo | — | **Session expired banner** on Main with one-tap Renew (distinct from T092 Load hint). |
| P1 | T146 | todo | — | **CSFloat enable requires Test success once** (optional Settings strict mode, default OFF). |
| P1 | T147 | todo | — | **Pending sales sort toggle**: newest / price high / name (pairs T073). |
| P1 | T148 | todo | — | **Hub: last Guard Load time** per account (prefs); “Loaded 5m ago”. |
| P2 | T149 | todo | — | **Reduce Main overflow clutter**: move CSFloat + export under “Account tools” submenu. |
| P2 | T150 | todo | — | **Accessibility: contentDescription** on confirm Accept/Decline and CSFloat Test/Refresh. |
| P2 | T151 | todo | — | **Landscape Hub**: two-pane list + preview code (phones/tablets). |
| P2 | T152 | todo | — | **Backup reminder**: if no export in 30 days, gentle Hub banner (dismissible 7d). |

### Acceptance (T141–T148)

- T141: visual only + existing T103 tap; no extra HTTP
- T142: pull triggers same load path as button; respects 429 UX (T101)
- T143: string includes account/label; no steamId secrets beyond what’s already shown
- T144: sticky code uses active account; period-aligned updates; no clipboard force
- T145: banner only when session known-bad/expired; Renew uses existing path; no getlist
- T146: strict mode OFF by default; when ON, enable blocked until Test ok for that steamId
- T147: sort persists per account; default current order
- T148: update timestamp only on successful Load; clear on account delete

### Acceptance (T149–T152)

- T149: no lost actions; deep links/menus still reachable
- T150: TalkBack announces actions; no behavior change
- T151: phone portrait unchanged; large width shows preview
- T152: never blocks app use; tracks last export time from existing export paths

### Acceptance (T129–T135)

- T129: Hub shows steamId secondary text; Keep-both duplicates no longer look identical
- T130: only for multi-file; single-file UX unchanged; Cancel still aborts remaining
- T131: one summary after batch; failures include reason short text
- T132: no partial write; user can cancel; message names missing field
- T133: invalid chars rejected; collision → prompt; native reload
- T134: Preview Confirm → then existing conflict flow; Cancel = no disk changes
- T135: labels/widgets/CSFloat keys remain valid for steamId after Replace

### Acceptance (T136–T140)

- T136: shareable text/csv; zero secrets/shared_secret/identity_secret
- T137: falls back to filename stem if blank
- T138: skipable; writes AppSettings label only
- T139: progress cancelable; already-imported counts in T131
- T140: safe delete of orphan session files only; never deletes mafiles

### T088 — **done** inside T068 (`969e36b`)

Pending Refresh syncs `setLastQueuedCount` without notification; strip refreshes.

### Acceptance (T077) / (T072) / (T079)

- T077: request permission on enable (API 33+); denied = silent poll OK
- T072: show balance + pending after successful `/me` Test; clear with key
- T079: Check-now one-shot unique work; disable button while running

### Acceptance (T076) / (T084) / (T085) / (T089) / (T090)

- T076: capped `/me` actionable hint skip (**1.6.16**)
- T084–T090: **done** (1.6.8–1.6.13)

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
| P1 | T092 | done | Worker | Offline / session hint on Confirm Load (+ Renew) → **1.6.17**. |
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
| P2 | T091 | done | Worker | Hub long-press copy SteamID. |
| P2 | T093 | todo | — | Share 2FA via share sheet. |
| P2 | T096 | doing | Worker | Confirmation relative time + type icon → **1.6.18** (NOW). |
| P3 | T052 | todo | — | PIN attempt lockout / backoff. |

---

## Done / deferred

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P0 | T076 | done | Worker | Cheap `/me` actionable probe (**1.6.16**) |
| P1 | T097 | done | Worker | Pending empty Check-now CTA (**1.6.15**) |
| P1 | T091 | done | Worker | Hub long-press copy SteamID (**1.6.14**) |
| P1 | T084 | done | Worker | Per-account CSFloat notify mute (**1.6.13**) |
| P1 | T085 | done | Worker | Notify on new CSFloat trade ids (**1.6.12**) |
| P0 | T129 | done | Worker | Hub secondary SteamID line (**1.6.11**) |
| P0 | T141 | done | Worker | Status strip accent when pending N&gt;0 (**1.6.10**) |
| P0 | T090 | done | Worker | Dual-bot warning on CSFloat enable (**1.6.9**) |
| P0 | T079 | done | Worker | Check now one-shot WorkManager (**1.6.8**) |
| P1 | T089 | done | Worker | CSFloat notif icon → launcher mipmap (inside 1.6.8) |
| P0 | T077 | done | Worker | POST_NOTIFICATIONS on CSFloat enable (**1.6.7**) |
| P1 | T072 | done | Worker | Balance line after Test (inside 1.6.7) |
| P0 | T103 | done | Worker | Tap status strip → pending (**1.6.6**) |
| P0 | T065 | done | Worker | Import SteamID conflict Replace/Keep both/Cancel (**1.6.5**) |
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
- **Stopped after T142 (1.6.21).** Queue idle — do not assign further NOW without user request. **T020** gated.
- Cycles 13–14 seeded through **T178**. Architect docs-only; no Kotlin / watchers.
- **Sole-Gradle rule:** at most one packaging `gradle assemble*` / isolated build at a time (file locks / daemon races).
- T076–T106 Boss-approved. Architect docs-only; no Kotlin / `event_wake` watchers.
- Boss docs-only for queue; no Kotlin fights.
