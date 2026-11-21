# MSDA

MSDA is a modern Steam authenticator and confirmation manager built for people who manage real accounts, real inventory value, and real-time market activity.

If you are tired of slow confirmation flows, weak account tooling, or clunky backup/export processes, MSDA gives you a focused mobile control panel for Steam security and operations.

## Why MSDA is useful

MSDA is designed to help you:
- protect account access with strong local app lock controls,
- manage multiple Steam accounts from one clean hub,
- keep confirmations predictable with manual-only actions and trade auto-confirm you control,
- keep your account data portable with secure export workflows,
- handle high-volume confirmation activity with one-tap Accept all for the visible list.

This is especially useful for users with active market operations, multiple account setups, and strict security requirements.

## What this app does

### Security-first access
- 4-digit PIN lock on app open
- Optional biometric unlock (fingerprint)
- Per-account operational controls, including a trade auto-confirm toggle

### Multi-account management
- Import `.mafile` accounts into a single hub
- Fast account switching and account-level actions
- Account labels (main, bot, farm) shown next to accounts in the hub
- Long-press an account in the hub to copy its current 2FA code
- Swipe-to-delete account with confirmation prompt

### Steam authentication and confirmations
- Steam LoginV2 support
- Steam QR login scanner
- Single and batch confirmation handling, including Accept all for the visible list
- Manual refresh — confirmations are loaded only when you ask; nothing polls Steam in the background

### Confirmation actions (per account)
- Trade auto-confirm on manual load: with AUTO on, trades in the loaded list are accepted
- Market and gift auto-accept are off
- Account-level toggles (A account settings do not affect B account)

### 2FA home screen widget
- Live Steam Guard code widget for one chosen account
- Pick the account when you add the widget; the code updates on the home screen

### Proxy support (shared and per account)
- HTTP proxy support
- SOCKS proxy support
- Public and private proxy credentials
- Shared default proxy with a per-account override
- Proxy check in Settings shows OK/fail and your public IP
- Live proxy status indicator on account page (shown only when proxy is configured)

### Backup and portability
- Export all account mafiles to ZIP
- Export current account mafile from account page
- Share via Android chooser (Telegram, email, file manager, cloud)
- Temporary export folders are cleaned up after export

### Updates
- Update checker on hub launch offers the APK for a newer GitHub release
- Version footer in Settings shows "Update available" and opens the release on tap

Note: background confirmation checks, push notifications, and market/gift auto-accept were removed in v1.4.1 to avoid Steam rate limits. Confirmations are manual only; see `release-notes.md` for the version history.

## Repository structure

- native C++ core: `AndroidCppApp/src`
- Android app UI (Kotlin): `AndroidCppApp/packaging/app/src/main/java/com/msda/android`
- build/run automation for one-click Visual Studio flow: `AndroidCppApp/tools`

## Quick start

1. Open `MSDA.slnx` in Visual Studio.
2. Set `MSDA.AndroidPackage` as startup project.
3. Press `F5`.

The flow builds native libraries, assembles and installs the APK, then launches the app on a connected device (or emulator fallback).
