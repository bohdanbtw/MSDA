# v1.5.0

## What's Changed

- **Accept all visible confirmations** — One action to accept every confirmation currently shown on the account screen.
- **Account labels** — Tag accounts as main, bot, or farm so they’re easier to tell apart in the hub.
- **2FA home screen widget** — Pin a live Steam Guard code to your home screen; pick which account to show.
- **Hub long-press copy** — Long-press an account in the hub to copy its current 2FA code.
- **Proxy check** — Test your proxy from Settings; see OK/fail and your public IP.
- **Shared + per-account proxy** — Set a default proxy for all accounts, and override it per account when needed.
- **Update hint in Settings** — The version footer shows “Update available” when a newer GitHub release exists; tap it to open the release page.
- **Update checker** — On hub launch, the app can detect a newer GitHub release and offer the APK download.

### From 1.4.x (still in this build)

- **Trade auto-confirm on manual load** — With AUTO on, pending trades are accepted when you load/refresh the confirmation list (no background polling).
- **Manual confirmations** — Refresh only when you ask; no Steam rate-limit spam from background loops.

## How to use (highlights)

1. **Accept all** — Open an account, load confirmations, then use Accept all for the visible list.
2. **Labels** — Set main/bot/farm on an account so the hub shows the tag.
3. **Widget** — Add the MSDA 2FA widget → choose an account → code updates on the home screen.
4. **Proxy** — Settings → configure shared proxy (or per-account) → Check to verify OK/fail + IP.
