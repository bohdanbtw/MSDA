# v1.5.0

## What's Changed

- **Accept all visible confirmations** — One action to accept every confirmation currently shown on the account screen.
- **Account labels** — Tag accounts as main, bot, or farm so they’re easier to tell apart in the hub.
- **2FA home screen widget** — Pin a live Steam Guard code to your home screen; pick which account to show.
- **Hub long-press copy** — Long-press an account in the hub to copy its current 2FA code.
- **Proxy check** — Test your proxy from Settings; see OK/fail and your public IP.
- **Shared + per-account proxy** — Set a default proxy for all accounts, and override it per account when needed.
- **Open Release** — Jump to the GitHub release page from Settings.
- **Update checker** — The app can detect a newer GitHub release and offer the APK download.

### From 1.4.x (still in this build)

- **Trade auto-confirm on manual load** — With AUTO on, pending trades are accepted when you load/refresh the confirmation list (no background polling).
- **Manual confirmations** — Refresh only when you ask; no Steam rate-limit spam from background loops.

## How to use (highlights)

1. **Accept all** — Open an account, load confirmations, then use Accept all for the visible list.
2. **Labels** — Set main/bot/farm on an account so the hub shows the tag.
3. **Widget** — Add the MSDA 2FA widget → choose an account → code updates on the home screen.
4. **Proxy** — Settings → configure shared proxy (or per-account) → Check to verify OK/fail + IP.

# v1.4.2

## What's Changed

- **Trade auto-confirm is back (manual only)** — Turn on AUTO for an account, then press Load / refresh confirmations. Pending trades in that list are accepted automatically.
- **Still no background spam** — Nothing polls Steam in the background. Auto-accept runs only after you load the confirmation list yourself.
- **Market / gift auto-confirm stay off** — Only trade confirmations are auto-accepted from the loaded list.

## How to use

1. Open an account → tap **AUTO** → enable trade auto-confirm.
2. Tap the refresh button (or Load confirmations).
3. Trades from that list are accepted; remaining confirmations stay on screen.

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

# v1.4.0

## What's Changed

- **Login & session fixes** — Fixed cases where the app accepted your password but confirmations or QR login still failed, or asked for the password again after reopening the app.
- **Sessions that actually stick** — After you log in once, your session is saved properly and restored on restart. No more losing login state because old saved data overwrote a fresh session.
- **Automatic session refresh** — The app renews your Steam session in the background (about every 15 minutes) and when you open confirmations or QR. Access tokens refresh silently; if that fails, the app can log in again using your saved password.
- **Fewer password prompts** — Your password is stored securely on the device after a successful login, so the app can recover the session on its own instead of asking you every few days.
- **Smarter Steam Guard on login** — The app only sends a Guard code or mobile confirmation when Steam actually requires it, and uses correct Steam server time for codes.
- **QR scanner** — QR login approval now keeps the same session as the rest of the app, so it continues to work after you approve a login on another device.
- **Auto-confirm improvements** — Market, trade, and gift auto-confirm now works while the account screen is open, not only when the app is closed. The AUTO dialog and Settings explain when background checks are required.
- **Network resilience** — Confirmation requests retry once on temporary SSL or timeout errors, with a clearer message instead of a raw handshake error.

## What was going wrong (and why)

- Steam’s mobile login API expects a different session format than the old flow used. The app often saved the wrong cookie type, so confirmations and QR broke even when login looked successful.
- Session tokens were not always written back to your mafile, or an outdated copy was loaded on startup — so the app “forgot” you were logged in.
- Login requests sometimes failed with a generic error because Steam’s response was misread.
- Auto-confirm only ran in the background worker, so it did nothing while you had the account screen open.

## What you get now

- Log in once → confirmations, 2FA codes, and QR approval should keep working across app restarts.
- Background refresh helps the session stay alive even if you don’t open the app for several days (unless Steam revokes the session — e.g. password change or “deauthorize all devices”).
- Auto-confirm: works on the open account screen; for closed app, enable **Settings → Allow background confirmations check** (the app will offer this when you turn on AUTO).
- Same mafile format as before; existing accounts and backups remain compatible.

# v1.3.0

## What's Changed

- **C++ core optimizations** — Native library runs faster and uses less memory thanks to streamlined hot paths and in‑lining improvements.
- **KeyStore‑based security** — Passwords and secrets are now protected by Android's hardware‑backed KeyStore, making offline extraction practically impossible.
- **Cookie lifecycle improvements** — Dead session cookies are automatically revived using a stored password fallback. No need to re‑enter your password each time cookies expire; the app quietly restores the session behind the scenes.
- **Build fixes** — The build script no longer throws a `Join‑Path` positional‑parameter error when `ANDROID_NDK_HOME` is set. Version code is correctly aligned with the release.
- **Versioning** — Updated `app.version.code` to `5`; bumped target API level compatibility.
