# CSFloat notes (from bohdanbtw/botCsFloat)

Reference: shallow clone of https://github.com/bohdanbtw/botCsFloat (Python seller bot + Telegram UI).
Purpose: map what MSDA can reuse on Android without copying secrets or breaking Guard rate limits.

## APIs (CSFloat `https://csfloat.com/api/v1`)

Auth header: `Authorization: <CSFLOAT_API_KEY>` (Profile → Developer). Also send JSON Accept/Content-Type.

| Method | Path | Role |
|--------|------|------|
| GET | `/me` | Profile, balance, away flag |
| PATCH | `/me` | `{ "away": bool }` — stall Online/Offline |
| GET | `/me/inventory` | Seller inventory / stall stock |
| GET | `/me/trades?state=&role=&page=&limit=` | Trades (`queued,pending`, history states) |
| POST | `/trades/{id}/accept` | Accept a queued sale |
| POST | `/trades/bulk/accept` | `{ "trade_ids": [...] }` |
| POST | `/trades/{id}/cannot-deliver` | Seller cannot send item |
| POST | `/trades/steam-status/new-offer` | Notify CSFloat of sent Steam offer |
| POST | `/trades/steam-status/offer` | Update offer state (`sent_offers` objects) |
| PATCH | `/listings/{id}` | `{ "price": cents }` reprice |
| GET | `/me/transactions?type=&page=&limit=` | Ledger (`trade_verified`, etc.) |
| GET | `/me/notifications?limit=` | Unread sale/trade hints |

Client behavior of note: retry on 429 (backoff), network errors, and non-JSON bodies; poll interval env `POLL_SECONDS` (default ~15–20s) is **CSFloat only**.

## Auth model (bot vs MSDA)

- **CSFloat:** API key only (no OAuth in this bot).
- **Steam:** maFile (`shared_secret`, `identity_secret`, `Session.RefreshToken`) + optional password fallback; session cookies under `data/`.
- **Telegram:** bot token + chat id (desktop/VPS UX — not required for MSDA core).

MSDA already owns Steam session / Guard; do **not** duplicate maFile export into a second always-on process on the same phone without a clear ownership model.

## Sale pipeline (bot)

1. Poll CSFloat for `queued`/`pending` seller trades.
2. Accept on CSFloat when needed.
3. Send Steam trade offer to buyer (trade URL/token from sale).
4. Enqueue **Guard confirm for that offer id only** (`GuardConfirmService`).
5. Notify Telegram; append sales log / ledger.

**Hard rule (also in MSDA PROTOCOL):** no timer-based Steam Guard polling. `mobileconf/getlist` only when a queued offer is NeedsConfirmation (state 9). Floors: getlist ≥35s, GetTradeOffers ≥20s; 429 → long cooloff.

## What can run on Android (MSDA)

Feasible / desirable:

- Opt-in per Steam account: store CSFloat API key in app settings (encrypted prefs), enable flag, poll interval.
- Lightweight WorkManager / background jobs: poll `/me/trades` for queued sales; surface notifications; optional accept + hand off to existing trade/confirm UX.
- Stall status: `/me` balances, away toggle.
- Guard confirm **only** when MSDA is about to confirm a CSFloat-driven offer (reuse existing confirmation path — no new Guard spam loop).

Poor fit / keep on VPS bot for now:

- Full Telegram UI, analytics charts, bulk auto-reprice loops, long-lived Docker/systemd process.
- Aggressive multi-account stall management while the phone sleeps (battery + Doze).

## Risks

- **Steam Guard 429 / lockouts** if confirm or getlist is polled on a timer.
- **API key + session leakage** if logged or committed; never put keys in git.
- **CSFloat ToS / rate limits** — respect 429 backoff; avoid sub-15s polling on mobile.
- **Battery/network** — WorkManager constraints (unmetered optional, battery not low); default poll slower than VPS bot.
- **Dual automation** — phone + VPS both accepting/confirming the same sales → race conditions; one owner per account.

## Phased plan for MSDA

1. **Docs + settings stubs (NOW):** package `com.msda.android.csfloat`, per-account toggle + API key field stubs, WorkManager skeleton; version **1.5.1**.
2. **Read-only:** authenticated `/me` + queued trades list in UI; no auto-accept.
3. **Opt-in accept + trade send:** wire to existing Steam offer helpers; Guard confirm only for that offer.
4. **Hardening:** battery budget, 429 cooloff, conflict detection with external botCsFloat, sales notifications.
5. **Later (optional):** listing reprice, away toggle, ledger sync — only if still needed after phone-side sales flow works.
