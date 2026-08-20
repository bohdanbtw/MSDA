# CSFloat notes (from bohdanbtw/botCsFloat)

Reference: local clone `D:\Programming\botCsFloat` / https://github.com/bohdanbtw/botCsFloat  
Purpose: map what MSDA can reuse on Android without copying secrets or breaking Guard rate limits.

## APIs (CSFloat `https://csfloat.com/api/v1`)

Auth header: `Authorization: <CSFLOAT_API_KEY>` (raw key, **not** `Bearer`). JSON Accept/Content-Type.

| Method | Path | Role |
|--------|------|------|
| GET | `/me` | Profile, balance, `away`, `actionable_trades` |
| PATCH | `/me` | `{ "away": bool }` — stall Online/Offline |
| GET | `/me/inventory` | Seller inventory / stall stock |
| GET | `/me/trades?state=&role=&page=&limit=` | Trades (`queued,pending`, history) |
| GET | `/me/notifications?limit=` | Unread sale/trade hints |
| POST | `/trades/{id}/accept` | Accept a queued sale |
| POST | `/trades/bulk/accept` | `{ "trade_ids": [...] }` |
| POST | `/trades/{id}/cannot-deliver` | Seller cannot send item |
| POST | `/trades/steam-status/new-offer` | Notify CSFloat of sent Steam offer |
| POST | `/trades/steam-status/offer` | Update offer state (`sent_offers`) |
| PATCH | `/listings/{id}` | `{ "price": cents }` reprice |
| GET | `/me/transactions?type=&page=&limit=` | Ledger (`trade_verified`, etc.) |
| GET | `/listings?...` | Market comps (429-heavy — avoid on phone loops) |

Bot client notes: process-wide min interval ~1s; on 429 cooloff 5–90s + honor `Retry-After`; CSFloat poll default **15–20s** (VPS). Phone must be **slower**.

## Auth model (bot vs MSDA)

| Layer | Bot | MSDA phone |
|-------|-----|------------|
| CSFloat | API key env | Per-account key in secure store (Keystore AES), default **OFF** |
| Steam Guard | maFile + confirm queue | Already on-device (`identity_secret`); **sole owner of confirms** |
| Steam session | cookies/JWT under `data/` | Existing `SessionStore` / mafile Session |
| Telegram | required UX | **Out of scope** for MSDA core |

**Dual-host warning:** do not run VPS botCsFloat + phone CSFloat worker on the **same** Steam account without one clear owner for offers + Guard (refresh-token / IP subject races).

## Sale pipeline (bot) — target for MSDA

```
queued → POST accept → pending → send Steam offer
      → steam-status notify → Guard confirm (offer state 9 only)
      → done when steam_offer.state ∈ {3, 11} or verify_sale_at
```

**Hard rule (PROTOCOL):** never timer-poll Steam `mobileconf/getlist`. Confirm only when a CSFloat-driven offer id is whitelisted and NeedsConfirmation. Floors borrowed from bot: getlist ≥35s, GetTradeOffers ≥20s; Steam 429 → long cooloff.

## On-device settings shape (implemented / target)

Package: `com.msda.android.csfloat`

| Key | Store | Default |
|-----|-------|---------|
| `enabled_<steamId>` | plaintext prefs `msda_csfloat_ui` | `false` |
| `interval_min_<steamId>` | same | **30** min (clamp 15–240; WorkManager floor 15) |
| API key | `CsFloatSecureStore` (Keystore) | empty |

Worker must never start unless `enabled && hasApiKey`. Clearing account removes both flag and key.

## Battery / network budget (phone)

| Constraint | Guidance |
|------------|----------|
| Poll cadence | Default **≥30 min** periodic WorkManager; never sub-15 min |
| Cheap probe | Prefer `GET /me` / `actionable_trades` before full trades list |
| Network | Prefer `NetworkType.CONNECTED`; optional unmetered toggle later |
| Battery | `RequiresBatteryNotLow = true` for periodic work |
| Doze | Accept deferred runs; surface “last checked” in UI |
| Steam | **Zero** background Guard polling from CSFloat worker |
| Logging | Never log API keys or `identity_secret` |

## Confirmation safety checklist (must ship before auto-confirm)

1. Confirm **only** offer ids enqueued from CSFloat pending sales (whitelist), never “accept all”.
2. Match Steam confirmation `creator_id` to trade offer id; optionally verify `given_asset_ids`.
3. Bound attempts + drop stale jobs (>1h).
4. Asset / unknown terminal state → **drop**, do not mark sold.
5. User-visible audit: last sale id, offer id, confirm result.
6. Per-account kill switch clears whitelist + cancels WorkManager for that steamId.

## T021 design sketch (Architect Cycle 2)

**Persist** per-steamId `GuardConfirmQueue` jobs: `{ offerId, assetIds, readyAtMs, createdAtMs, attempts, meta }`.  
Constants (from botCsFloat): getlist floor **35s**, GetTradeOffers floor **20s**, backoff 25/45/90/180s, max **5** attempts, drop age **>1h**, Steam 429 cooloff multi-minute.

**Sequence (event-driven):** T020 enqueue → one-shot WorkManager → GetTradeOffers (≥20s) → if any job state **9** and cooloff clear → getlist once (≥35s) → match `creator_id` → `ConfirmationService.respondItemWithRenew` for that item only → audit → reschedule only if jobs remain. Empty queue = no Steam Guard traffic.

**New modules (suggested):** `GuardConfirmQueue.kt`, `CsFloatGuardWorker.kt`, `SteamTradeOffersApi.kt`.  
**Do not** reuse MainActivity “auto-accept all type=2” loop. `CsFloatSaleWorker` stays CSFloat-HTTP only.

## What MSDA should ship (phased)

| Phase | Scope | Status target |
|-------|--------|---------------|
| **1 Scaffold** | Package, models, client stub/`/me`, secure key store, settings flags, optional WorkManager skeleton **disabled by default**, bump **1.5.1** | T010 |
| **2 Credentials UI** | Per-account CSFloat screen: enable, API key, test `/me`, interval | T011 |
| **3 Read-only sales** | Foreground list of queued/pending; notification on new actionable | T012 |
| **4 Accept + offer** | Opt-in accept; send Steam offer; steam-status; **no** auto Guard yet | T020 |
| **5 Safe confirm** | Whitelist Guard only for CSFloat offer ids; floors + 429 cooloff | T021 |
| **6 Harden** | Dual-bot conflict banner, battery budget doc in UI, sales log | T022 |
| **Later** | Away toggle, reprice, ledger — only if still needed | backlog |

**Out of first scope:** buy orders, market neighbor search loops, Telegram, bulk auto-reprice.

## Risks

- Steam Guard 429 / lockouts from any new getlist timer.
- API key leakage in logs, exports, or shared backups.
- CSFloat ToS / 429 — respect cooloff; no aggressive mobile polling.
- Phone + VPS both accepting the same sales → races.
- Accept-all / trade auto-confirm already lack pacing in MSDA foreground — do not compound with CSFloat spam.
