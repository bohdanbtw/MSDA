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
