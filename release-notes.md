# v1.3.0

## What's Changed

- **C++ core optimizations** — Native library runs faster and uses less memory thanks to streamlined hot paths and in‑lining improvements.
- **KeyStore‑based security** — Passwords and secrets are now protected by Android’s hardware‑backed KeyStore, making offline extraction practically impossible.
- **Cookie lifecycle improvements** — Dead session cookies are automatically revived using a stored password fallback. No need to re‑enter your password each time cookies expire; the app quietly restores the session behind the scenes.
- **Build fixes** — The build script no longer throws a `Join‑Path` positional‑parameter error when `ANDROID_NDK_HOME` is set. Version code is correctly aligned with the release.
- **Versioning** — Updated `app.version.code` to `5`; bumped target API level compatibility.
