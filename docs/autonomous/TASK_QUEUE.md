# TASK_QUEUE

Prioritized backlog. **Architect** invents / refines, **Boss** orders NOW, **Worker** implements.

Status legend: `todo` | `doing` | `done` | `blocked` | `deferred`

**Boss rule:** at most **1–3** tasks in `doing` / NOW. Worker takes only the NOW block.  
**Architect rule:** docs-only edits; do not fight Worker on Kotlin files.

App HEAD: **1.6.0** (`8b25eb6` T012). Boss: T012 **APPROVED**. Single NOW = **T030**.

---

## NOW (Worker — start immediately)

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P0 | T030 | doing | Worker | **Widget tap-to-copy 2FA (1.6.1).** Tap widget copies current code + brief Toast; fix countdown when bound by `steamId` only (seconds must not stick at `-1`). Touch `Code2FAWidgetProvider` (+ related) only. No Steam confirmation / CSFloat changes. Bump `1.6.1` / `160001`. DEV_LOG + push. |

### Acceptance (T030)

- [ ] Tap widget copies current code (works when app backgrounded); brief Toast
- [ ] Account title / long-press still opens configure (don’t lose rebind)
- [ ] Countdown shows 0–30 correctly for **steamId**-bound widgets without mutating native active account
- [ ] No new Steam getlist / confirmation polling; no CSFloat changes
- [ ] Version `1.6.1` / `160001`; DEV_LOG + push

### Acceptance (T012) — **Boss APPROVED** (`8b25eb6`, v1.6.0)

- [x] Expanded `CsFloatTradeSummary` + parse (name, price, buyer, asset, offer)
- [x] Pending sales dialog: read-only list + Refresh; empty/401/429/network
- [x] No accept / offer / Guard
- [x] Version **1.6.0** / `160000`

### Acceptance (T060) — **Boss APPROVED** (`2fb1410`, v1.5.6)

- [x] Settings Switch default **OFF**; explains no confirmation polling
- [x] ON → schedule; OFF → cancel; worker early-exits if pref OFF
- [x] `BackgroundSyncScheduler.disable` does **not** cancel renewal
- [x] By-steamId renewal only; zero getlist/confirm from worker
- [x] Version **1.5.6** / `150006`

---

## NEXT (after T030)

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P0 | T013 | todo | — | **CSFloat notification on actionable trades.** Count-delta notify; tap → Pending sales. No Steam Guard. |
| P1 | T068 | todo | — | **CSFloat status strip:** last-checked + queued count on settings dialog (write from T012 refresh + SaleWorker). |
| P1 | T072 | todo | — | **CSFloat dialog balance line** from `/me` (balance + pending) after Test/Refresh. |
| P1 | T063 | todo | — | **Confirm All type breakdown + trade friction.** Counts by type; trades need extra checkbox (default off). |
| P1 | T065 | todo | — | **Import conflict by SteamID** — Replace / Keep both / Cancel. |
| P1 | T064 | todo | — | **Dual SDA export:** Secrets-only vs Full SessionData. |
| P1 | T031 | todo | — | **Hub search/filter** by name + label. |
| P1 | T042 | todo | — | **Wire or remove** dead market/gift auto-confirm APIs. |
| P1 | T020 | todo | — | **Opt-in CSFloat accept + Steam offer send** (second toggle default OFF); enqueue offer id; **no** auto Guard. |
| P1 | T021 | todo | — | **Safe CSFloat Guard confirm** — whitelist only; floors; audit (CSFLOAT_NOTES T021). |
| P2 | T022 | todo | — | Dual-bot conflict banner + sales audit UI. |
| P2 | T069 | todo | — | **CSFloat Away toggle** (`PATCH /me` `{away}`). |
| P2 | T070 | todo | — | Hub row badge when CSFloat enabled. |
| P2 | T073 | todo | — | Pending-sales list polish: sort by state then price; show offer-state chip when present. |
| P2 | T062 | todo | — | Encrypt mafile secrets at rest (Keystore). |
| P2 | T066 | todo | — | Period-aligned 2FA UI tick (Main + widget). |
| P2 | T067 | todo | — | Hub multi-select: renew selected + panic disable auto-confirm. |
| P2 | T061b | todo | — | Tighten `looksLikeClockSkew` (broad `"invalid"`). |
| P2 | T074 | todo | — | **Sensitive clipboard** on 2FA copy (Main/Hub): `ClipDescription.EXTRA_IS_SENSITIVE` / hide from keyboard suggestions where API allows. |
| P2 | T075 | todo | — | Hub: show “session expiring soon” affordance that jumps to renew (pairs with T032). |

### Acceptance (T013)

- [ ] `CsFloatSaleWorker` compares queued/actionable count vs last-seen; notify only on **increase**
- [ ] Tap opens Pending sales (or Main with CSFloat for that steamId)
- [ ] Low-importance channel; OS mute ≠ clear API key
- [ ] Zero Steam Guard; honor 429; interval floor ≥15m
- [ ] Writes same last-checked prefs as T068 when possible

### Acceptance (T068)

- [ ] Persist `last_checked_ms_<steamId>` + `last_queued_count_<steamId>` from Pending Refresh + SaleWorker
- [ ] CSFloat settings dialog shows “Last checked … · N pending” (or Never)
- [ ] Clear key / disable clears prefs

### Acceptance (T072)

- [ ] After successful `/me` (Test connection), show balance + pending in CSFloat dialog
- [ ] No extra polling; clear with key

### Acceptance (T063) / (T065) / (T069) / (T073)

- T063: type counts + trades checkbox default off; keep T041 pacing
- T065: same steamId → Replace / Keep both / Cancel; filename clash warned
- T069: Away PATCH; 401/429/network; no Steam
- T073: deterministic sort; offer-state visible when `steamOfferState` non-blank; still read-only

### Acceptance (T020–T021)

- See CSFLOAT_NOTES Phase 4–5 / T021 sketch. Second toggle default OFF; whitelist Guard only. **Gate:** T013 notify live + dual-bot warning copy present (T022 can ship with T021).

---

## UI polish (real-user value)

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P1 | T030 | todo | — | Widget copy + steamId countdown. |
| P1 | T031 | todo | — | Hub search/filter. |
| P1 | T034 | todo | — | Code monospace + letter-spacing; accurate progress. |
| P2 | T032 | todo | — | Session health chip (valid / expiring / needs login). |
| P2 | T033 | todo | — | Material confirmation rows + Coil/Glide (cancel on detach). |
| P2 | T035 | todo | — | Settings Preference-style sections. |
| P2 | T070 | todo | — | Hub CSFloat-enabled badge. |
| P2 | T073 | todo | — | Pending-sales sort + offer chip. |
| P3 | T071 | todo | — | Confirm Load empty-state hint + Renew shortcut. |

### Acceptance (T031) / (T034)

- T031: case-insensitive name+label filter; T040-safe delete while filtered
- T034: tabular/monospace digits; timer matches seconds-left within period

---

## Bugs / tech debt

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P1 | T042 | todo | — | Wire or remove market/gift auto-confirm dead API |
| P1 | T061b | todo | — | Tighten `looksLikeClockSkew` |
| P2 | T043 | todo | — | PasswordManager case unify |
| P2 | T044 | todo | — | Quarantine dead confirmation background leftovers |
| P2 | T045 | todo | — | Proxy passwords → Keystore |
| P3 | T046 | todo | — | Fix/delete orphaned `PasswordBackupHelper` |

---

## Smaller authenticator features

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P2 | T050 | todo | — | Clipboard auto-clear 2FA after copy (30–60s) — pairs with T074. |
| P2 | T051 | todo | — | Export mafile secrets warning dialog. |
| P3 | T052 | todo | — | PIN attempt lockout / backoff. |

### Acceptance (T050)

- [ ] Clear only if clipboard still holds MSDA’s 2FA clip; API 28+ sensitive/clear where available

---

## Done / deferred

| Priority | ID | Status | Owner | Task |
|----------|----|--------|-------|------|
| P0 | T012 | done | Worker | CSFloat read-only pending sales (**1.6.0**) |
| P0 | T060 | done | Worker | Opt-in session keep-alive (**1.5.6**) |
| P0 | T011 | done | Worker | CSFloat Test connection (**1.5.5**) |
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
- **Post-1.6.0 order:** **T030** (1.6.1) → **T013** (+ optional **T068**) → **T072** → **T063**/Hub polish → **T020** only after notify works.
- Do **not** auto-accept CSFloat sales yet; dual-bot race remains real.
- Architect docs-only; Worker owns Kotlin.
