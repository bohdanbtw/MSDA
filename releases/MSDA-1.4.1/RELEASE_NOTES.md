# v1.4.1

## What's Changed

- **No more Steam rate-limit spam** — The confirmation list no longer refreshes automatically every few seconds. That polling could trigger Steam’s 12-hour request timeout.
- **Manual confirmations only** — Tap the refresh button next to “Active confirmations” (or use Load confirmations in the menu) when you want to check Steam.
- **Auto-confirm removed** — Market / trade / gift auto-confirm and related settings are gone, so the app does not accept confirmations in the background.
- **Background sync removed** — Background confirmation checks, push polling, and automatic multi-account session renewal on hub open are disabled.
- **Still works on demand** — Login, 2FA codes, QR approval, and manual accept/decline are unchanged. After you accept or decline, the list refreshes once.

## What you get now

- Open an account → tap refresh when you need confirmations → accept/decline as usual.
- Leaving the app open no longer hammers Steam with request loops.
- Same mafile format; existing accounts remain compatible.
