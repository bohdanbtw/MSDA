# TASK_QUEUE

Prioritized backlog. **Architect** invents / refines, **Boss** orders NOW, **Worker** implements.

Status legend: `todo` | `doing` | `done` | `blocked` | `deferred`

**Boss rule:** at most **1–3** tasks in `doing` / NOW. Worker takes only the NOW block.  
**Architect rule:** docs-only edits; do not fight Worker on Kotlin files.

App HEAD: **1.6.2** (`d91a475` T063). Boss: T030 + T063 **APPROVED**. Single NOW = **T013**.

---

## NOW (Worker — start immediately)

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P0 | T013 | doing | Worker | **CSFloat notification on new queued/pending sales (1.6.3).** Count-delta in `CsFloatSaleWorker`; notify only on increase; first run baseline only (no spam). Tap → Pending sales / Main for that steamId. Touch `csfloat/*` + notification channel only. **Zero Steam Guard / getlist / confirm.** Honor 429; interval floor ≥15m. Optional same-batch **T076** if small. Bump `1.6.3` / `160003`. DEV_LOG + push. |

### Acceptance (T013) — concrete

- [ ] After successful `listQueuedTrades` (or T076 cheap probe), compare `count` vs `last_queued_count_<steamId>`
- [ ] Notify **only** when `count > last` (first run: store baseline, **no** notify spam)
- [ ] Notification title/body: account label/name + “N pending CSFloat sale(s)” — never API key / secrets
- [ ] Tap → open Main for that account **or** Pending sales dialog (extra Intent extras OK)
- [ ] Channel `csfloat_sales`, importance DEFAULT or LOW; create once
- [ ] `clearAccount` / disable / clear key cancels notifications for that steamId + clears last_* prefs
- [ ] **Zero** Steam getlist/confirm/offer; honor 429 (no notify storm on retries)
- [ ] Version **1.6.3** / `160003`; DEV_LOG + push
- [ ] Prefer shipping **T076** in same batch if small; else follow immediately

### Acceptance (T063) — **Boss APPROVED** (`d91a475`, v1.6.2)

- [x] Accept-all dialog lists market / trade / other counts
- [x] Including trades requires checkbox (**default unchecked**)
- [x] Filtered multi-accept keeps ≥400ms pacing; stop on hard failure
- [x] Single-item accept/decline unchanged; no getlist timer
- [x] Version **1.6.2** / `160002`

### Acceptance (T030) — **Boss APPROVED** (`161dae2`, v1.6.1)

- [x] Tap widget code copies 2FA + Toast (background BroadcastReceiver)
- [x] Account title still opens configure
- [x] Countdown 1–30 for steamId-bound widgets without mutating native active account
- [x] No getlist / CSFloat changes; widget AlarmManager UI tick only
- [x] Version `1.6.1` / `160001`

### Acceptance (T012) — **Boss APPROVED** (`8b25eb6`, v1.6.0)

- [x] Expanded `CsFloatTradeSummary` + parse (name, price, buyer, asset, offer)
- [x] Pending sales dialog: read-only list + Refresh; empty/401/429/network
- [x] No accept / offer / Guard
- [x] Version **1.6.0** / `160000`

### Acceptance (T060) — **Boss APPROVED** (`2fb1410`, v1.5.6)

- [x] Settings Switch default **OFF**; no getlist; by-steamId renew
- [x] Version **1.5.6** / `150006`

---

## NEXT (after T013)

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P0 | T076 | todo | — | **Cheap `/me` actionable probe** — parse `actionable_trades` on `CsFloatMeSummary`; skip full trades list when unchanged. Pairs with T013. |
| P1 | T068 | todo | — | **CSFloat status strip:** last-checked + queued count on settings dialog (worker + Pending Refresh). |
| P1 | T077 | todo | — | **POST_NOTIFICATIONS runtime prompt** when enabling CSFloat on API 33+ (manifest already declares). Soft-fail if denied; still poll silently. |
| P1 | T072 | todo | — | **CSFloat dialog balance line** from `/me` after Test/Refresh. |
| P1 | T079 | todo | — | **Check now** one-shot WorkManager from CSFloat dialog (same path as periodic; no Steam). |
| P1 | T065 | todo | — | **Import conflict by SteamID** — Replace / Keep both / Cancel. |
| P1 | T064 | todo | — | **Dual SDA export:** Secrets-only vs Full SessionData. |
| P1 | T031 | todo | — | **Hub search/filter** by name + label. |
| P1 | T042 | todo | — | **Wire or remove** dead market/gift auto-confirm APIs. |
| P1 | T020 | todo | — | **Opt-in CSFloat accept + Steam offer send** (second toggle default OFF); **no** auto Guard. |
| P1 | T021 | todo | — | **Safe CSFloat Guard confirm** — whitelist only; floors; audit. |
| P2 | T022 | todo | — | Dual-bot conflict banner + sales audit UI. |
| P2 | T069 | todo | — | **CSFloat Away toggle** (`PATCH /me` `{away}`). |
| P2 | T070 | todo | — | Hub row badge when CSFloat enabled. |
| P2 | T073 | todo | — | Pending-sales sort + offer-state chip. |
| P2 | T074 | todo | — | Sensitive clipboard flag on Main/Hub/widget copy. |
| P2 | T075 | todo | — | Hub “session expiring soon” → renew. |
| P2 | T078 | todo | — | Widget a11y: `contentDescription` “Copy code” on code view; optional short haptic on copy. |
| P2 | T080 | todo | — | SaleWorker: drop PII from debug logs (username/balance) in release; keep steamId truncated if needed. |
| P2 | T050 | todo | — | Clipboard auto-clear 2FA after 30–60s (pairs with T074). |
| P2 | T062 | todo | — | Encrypt mafile secrets at rest (Keystore). |
| P2 | T066 | todo | — | Period-aligned 2FA UI tick (Main + widget). |
| P2 | T067 | todo | — | Hub multi-select renew + panic disable auto-confirm. |
| P2 | T061b | todo | — | Tighten `looksLikeClockSkew`. |
| P1 | T081 | todo | — | **Cancel CSFloat notif on disable/clear** — `CsFloatNotifier.cancel(steamId)` from `clearAccount` / setEnabled(false). |
| P1 | T084 | todo | — | **Per-account notify mute** (default ON): poll continues; no banners when muted. |
| P1 | T085 | todo | — | **Notify on new trade ids** (not only count↑) so replace-same-count sales still alert. |
| P2 | T086 | todo | — | Settings global CSFloat status: last worker run OK/429 + “open account” shortcut. |
| P2 | T087 | todo | — | Hub filter chips: All / Has session / CSFloat on (pairs with T031). |

### Acceptance (T076)

- [ ] `CsFloatMeSummary` exposes actionable/queued hint from `/me` when present
- [ ] Worker uses hint to skip trades list when unchanged vs last count
- [ ] If `/me` lacks field, fall back to current trades list behavior

### Acceptance (T068) / (T077) / (T072) / (T079)

- T068: show “Last checked … · N pending”; write from worker + Pending Refresh; clear with key
- T077: request POST_NOTIFICATIONS when user enables CSFloat on API 33+; denied = silent poll OK
- T072: balance + pending after successful `/me`; no extra polling
- T079: “Check now” enqueues unique one-time work; button disabled while running; same notify rules as T013

### Acceptance (T081) / (T084) / (T085)

- T081: disable or clear key cancels active notification for that steamId (id scheme matches notifier)
- T084: mute pref default true(=notify); OFF suppresses `notifyPendingIncrease` only
- T085: persist last seen trade id set (cap ~50); notify if any new id appears even when count flat/down-then-up edge cases documented

### Acceptance (T065) / (T073)

- T065: same steamId → Replace / Keep both / Cancel; filename clash warned
- T073: sort state then price; offer-state when non-blank; read-only

### Acceptance (T020–T021)

- CSFLOAT_NOTES Phase 4–5 / T021 sketch. **Gate:** T013 live + dual-bot warning (T022 can ship with T021).

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
| P3 | T071 | todo | — | Confirm Load empty-state + Renew shortcut. |

### Acceptance (T031) / (T034) / (T078)

- T031: case-insensitive name+label; T040-safe delete while filtered
- T034: tabular/monospace digits; timer matches seconds-left within period
- T078: TalkBack announces copy; optional `performHapticFeedback` on successful copy

---

## Bugs / tech debt

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P1 | T042 | todo | — | Wire or remove market/gift auto-confirm dead API |
| P1 | T061b | todo | — | Tighten `looksLikeClockSkew` |
| P2 | T043 | todo | — | PasswordManager case unify |
| P2 | T044 | todo | — | Quarantine dead confirmation background leftovers |
| P2 | T045 | todo | — | Proxy passwords → Keystore |
| P2 | T080 | todo | — | SaleWorker release log hygiene (no username/balance) |
| P3 | T046 | todo | — | Fix/delete orphaned `PasswordBackupHelper` |

---

## Smaller authenticator features

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P2 | T050 | todo | — | Clipboard auto-clear 2FA (30–60s). |
| P2 | T051 | todo | — | Export mafile secrets warning. |
| P2 | T074 | todo | — | Sensitive clipboard flag. |
| P3 | T052 | todo | — | PIN attempt lockout / backoff. |

### Acceptance (T050) / (T074)

- T050: clear only if clipboard still MSDA 2FA clip
- T074: mark clips sensitive on API that supports it (Main + Hub + widget)

---

## Done / deferred

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
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
- **Post-1.6.2 order:** finish **T013** (1.6.3) → **T068** (prefs already partially written by worker WIP) / **T077** / **T072** → **T081/T084/T085** reliability → Hub → **T020** only after notify proven.
- Manifest already has `POST_NOTIFICATIONS` (with T030) — T077 is runtime UX.
- Architect docs-only; never touch Kotlin / `event_wake` watchers while Worker owns app code.
- Boss docs-only for queue; no Kotlin fights.
