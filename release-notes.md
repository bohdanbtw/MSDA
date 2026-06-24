# v1.4.0

## What's Changed

- **Session revival rework** — Token renewal now uses `GenerateAccessTokenForApp/v1` (the same API the official Steam mobile app uses). No more `client_id` mismatch or partial sessions from the old `TokenRefresh` path.
- **website_id = Mobile** — Login sessions are now created with the correct mobile audience, matching the official Steam authenticator.
- **Mafile writeback** — After a successful login or silent renewal, updated session tokens (`sessionid`, `steamLoginSecure`, `refresh_token`, `access_token`) are written back to the `.mafile` on disk. Sessions survive app reinstalls and data-export/import cycles.
- **Encrypted SessionStore** — Session tokens (cookies, access/refresh tokens) are now encrypted with an Android Keystore-backed AES-256-GCM key, identical to the password protection layer.
- **JWT expiry tracking** — The `exp` field of access tokens is decoded and stored. The proactive renewal worker skips accounts whose sessions are still fresh and renews only those near or past expiry.
- **Proactive renewal always on** — The 15-minute background session refresh worker now runs unconditionally (not just when background confirmations are enabled), keeping sessions alive even when the app is opened after days.
- **Auth context sync after silent renew** — After a transparent token renewal, the in-memory auth reference in `MainActivity` and the background worker is immediately replaced with the fresh session so that accept/decline operations use the new cookies.
- **Retry depth limit** — `loadBundlesWithAutoRenew` now allows at most one renewal attempt per call, preventing infinite recursion on false-success responses.
- **QR approval session sync** — Tokens resolved during QR login approval are now written through `SessionPersistence` (encrypted store + native in-memory + mafile), closing a split-brain gap.
- **Legacy HTTP helper removed** — `HttpHelper.java` and the corresponding JNI `tryRefreshSession`/`reauthWithPassword` paths (which used a deprecated Steam endpoint with wrong username semantics) have been deleted.
- **Automatic password save** — The "Save Password / Don't Save" dialog is removed. The password is always stored securely in the Android Keystore after a successful interactive login, ensuring the silent renewal fallback is available without extra user action.

# v1.3.0

## What's Changed

- **C++ core optimizations** — Native library runs faster and uses less memory thanks to streamlined hot paths and in‑lining improvements.
- **KeyStore‑based security** — Passwords and secrets are now protected by Android's hardware‑backed KeyStore, making offline extraction practically impossible.
- **Build fixes** — The build script no longer throws a `Join‑Path` positional‑parameter error when `ANDROID_NDK_HOME` is set. Version code is correctly aligned with the release.
- **Versioning** — Updated `app.version.code` to `5`; bumped target API level compatibility.
