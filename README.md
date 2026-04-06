# MSDA

MSDA is a modern Steam authenticator and confirmation manager built for people who manage real accounts, real inventory value, and real-time market activity.

If you are tired of slow confirmation flows, weak account tooling, or clunky backup/export processes, MSDA gives you a focused mobile control panel for Steam security and operations.

## Why MSDA is useful

MSDA is designed to help you:
- protect account access with strong local app lock controls,
- manage multiple Steam accounts from one clean hub,
- reduce confirmation friction with safe automation options,
- keep your account data portable with secure export workflows,
- stay responsive to high-volume confirmation activity without constant manual refresh.

This is especially useful for users with active market operations, multiple account setups, and strict security requirements.

## What this app does

### Security-first access
- 4-digit PIN lock on app open
- Optional biometric unlock (fingerprint)
- Per-account operational controls for safer automation

### Multi-account management
- Import `.mafile` accounts into a single hub
- Fast account switching and account-level actions
- Swipe-to-delete account with confirmation prompt

### Steam authentication and confirmations
- Steam LoginV2 support
- Steam QR login scanner
- Single and batch confirmation handling
- Auto refresh while app is open

### Smart automation (per account)
- Auto-accept market confirmations
- Auto-accept trade confirmations
- Auto-accept gift-trade confirmations (conservative detection)
- Account-level toggles (A account settings do not affect B account)

### Background processing
- Background confirmations check
- Push notifications for pending confirmations
- Background work allowed when either confirmations-check or notifications is enabled

### Proxy support (per account)
- HTTP proxy support
- SOCKS proxy support
- Public and private proxy credentials
- Live proxy status indicator on account page (shown only when proxy is configured)

### Backup and portability
- Export all account mafiles to ZIP
- Export current account mafile from account page
- Share via Android chooser (Telegram, email, file manager, cloud)
- Temporary export folders are cleaned up after export

## Repository structure

- native C++ core: `AndroidCppApp/src`
- Android app UI (Kotlin): `AndroidCppApp/packaging/app/src/main/java/com/msda/android`
- build/run automation for one-click Visual Studio flow: `AndroidCppApp/tools`

## Quick start

1. Open `MSDA.slnx` in Visual Studio.
2. Set `MSDA.AndroidPackage` as startup project.
3. Press `F5`.

The flow builds native libraries, assembles and installs the APK, then launches the app on a connected device (or emulator fallback).
