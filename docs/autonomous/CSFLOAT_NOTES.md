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
| **1 Scaffold** | Package, models, client `/me`, secure key store, settings flags, WM skeleton default OFF | T010 **done** (1.5.1) |
| **2 Credentials UI** | Enable, API key, test `/me`, interval, clear key | T011 **done** (1.5.5) |
| **3 Read-only sales** | Expand DTO + foreground queued/pending + Refresh | T012 **done** (1.6.0) |
| **3b Notify + status** | Count-delta notify (**done 1.6.3**); status strip; balance; Check-now; cheap probe | T013 **done**; T068 + T072 + T076 + T079 next |
| **4 Accept + offer** | Opt-in accept; send Steam offer; steam-status; **no** auto Guard | T020 |
| **5 Safe confirm** | Whitelist Guard only for CSFloat offer ids; floors + 429 cooloff | T021 |
| **6 Harden** | Dual-bot conflict banner, battery budget in UI, sales log | T022 |
| **Later** | Away toggle (T069), reprice, ledger | backlog |

### T013 notify — **shipped 1.6.3**

Baseline-then-increase; tap → Pending sales; clearAccount cancels. Follow-ups: T068 strip, T085 trade-id set, T084 mute, T090 dual-bot warning before accept.

### T012 UI (shipped) / follow-ups

- Shipped: Main CSFloat dialog → **Pending sales** AlertDialog, Refresh, row = name · price · state · buyer.
- Follow-ups: T068 last-checked on parent dialog; T073 sort + offer-state chip; T013 notify on count increase.
- Explicit non-goals until T020: accept, offer send, Guard.

**Out of first scope:** buy orders, market neighbor search loops, Telegram, bulk auto-reprice.

## T012 DTO fields — **shipped in 1.6.0**

`CsFloatTradeSummary` now includes the fields below (parse in `listQueuedTrades`). Keep POST accept out until T020.

| Field | Source (bot) | UI use |
|-------|--------------|--------|
| `buyerSteamId` | `buyer.steam_id` | Show counterparty |
| `priceCents` | listing/contract price | Sort / display |
| `marketHashName` | `item.market_hash_name` | Primary label |
| `assetId` | `item.asset_id` | Later Guard match |
| `floatValue` | optional | Detail line |
| `steamOfferId` / `steamOfferState` | `steam_offer` | Pending vs needs confirm |
| `tradeUrl` / token | trade fields | Later T020 send |

Parse in `CsFloatClient.listQueuedTrades()`; keep POST accept out of T012.
- API key leakage in logs, exports, or shared backups.
- CSFloat ToS / 429 — respect cooloff; no aggressive mobile polling.
- Phone + VPS both accepting the same sales → races.
- Accept-all / trade auto-confirm already lack pacing in MSDA foreground — do not compound with CSFloat spam.
