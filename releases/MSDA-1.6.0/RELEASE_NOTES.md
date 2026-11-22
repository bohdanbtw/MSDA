# v1.6.0

A stabilization and security release over v1.5.0, validated on a real Android 15 emulator.

## What's Changed

### Steam login
- Restored password login. Steam no longer returns `sessionid` on `/login/home/`; the app now generates a Steam-format CSRF token locally (12 random bytes, 24 lower-case hex characters) and stores it as the matching cookie.
- Fixed a `uint64` parsing bug that rejected valid login responses whenever Steam's `clientId` had the high bit set (`clientId <= 0` → `clientId == 0`). Live login now returns a complete session with refresh and access tokens.
- Added regression tests for the generated session ID format and for high-bit `clientId` values.

### Security and account safety
- Closed the app-lock bypass: `HubActivity` is no longer externally launchable, all screens share a lock gate that re-locks on backgrounding, and the lock screen uses `FLAG_SECURE`. PIN uses PBKDF2 (120k iterations) with failed-attempt backoff and re-auth for security changes.
- Export and delete select accounts by parsed Steam ID instead of substring matching; mafile import paths are sanitized against traversal.
- Keystore keys are never silently regenerated on read errors; proxy passwords are encrypted; error messages are redacted so tokens cannot leak to the UI or logs.
- APK self-updates verify the signing certificate before installation.

### 2FA widget
- Removed the 1-second waking alarm; the countdown uses a Chronometer with roughly 30-second boundary alarms.
- Code and timer reads are account-scoped, so a widget refresh can no longer change which account's code the foreground app copies.

### Sessions, background and native core
- Per-account session locks, a single authoritative session writer, and atomic mafile writes. QR approval keeps the refreshed token.
- Removed dead background-sync / WorkManager code and aligned the README with actual behavior.
- Native core: JNI exception containment, hardened JSON parsing, Base64 undefined-behaviour fix, strict 20-byte secret validation, and a new CTest/KAT suite.

### Build and delivery
- `targetSdk 35`, version centralized in `gradle.properties`, R8 minification with JNI keep rules, and both `arm64-v8a` and `x86_64` native libraries.
- Verified with `gradle testDebugUnitTest assembleRelease`, native CMake/CTest, and a live install + login test on an Android 15 emulator.
- Release APKs are signed with the project release keystore, so installs are upgrades rather than reinstalls.

## Notes
- A password prompt on a fresh install is expected until the first successful login; the saved session is reused afterwards.
- Background confirmations, push notifications, and market/gift auto-accept remain disabled to avoid Steam rate limits (decision from v1.4.1).
