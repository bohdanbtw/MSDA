# MSDA Architecture (Detailed)

## 1. High-level architecture

MSDA is split into 3 layers:

1. **Native core (C++)**
   - Account loading/parsing and active-account state.
   - JNI bridge entry points for Android app.

2. **Android application (Kotlin)**
   - Hub, account details, settings UI.
   - Steam auth/session refresh and confirmations networking.
   - Local settings and session persistence.

3. **Build/launch orchestration (PowerShell + Visual Studio project)**
   - Native Android build (`cmake + ninja`)
   - APK assemble/install (`gradle`)
   - Target launch (USB-first, emulator fallback)

---

## 2. Native core

### Main files

- `src/msda/AccountManager.*`
- `src/msda/MafileParser.*`
- `src/android/jni_bridge.cpp`

### Responsibilities

- Import all `.mafile` files from folder.
- Track account list and active account index.
- Provide code and auth payload for current account.
- Expose JNI methods consumed by Kotlin (`NativeBridge.kt`).

---

## 3. Android UI modules

### `HubActivity`

- Launcher screen.
- Lists accounts from native bridge.
- Select account -> opens account details.
- Entry point for add-account and settings page.

### `MainActivity`

- Account detail window.
- Shows active 2FA code and time progress.
- Loads confirmations list and supports:
  - per-item approve/decline
  - bundle approve/decline
- Re-login action (Steam LoginV2) available from quick menu.

### `SettingsActivity`

- Theme settings (System/Light/Dark).
- Background checks on/off.
- Push notifications on/off (dependent on background mode).
- App footer version.

---

## 4. Authentication flow

Main implementation: `SteamAuthService.kt`

1. Fetch Steam RSA public key.
2. Begin LoginV2 session with encrypted credentials.
3. Submit Steam Guard code.
4. Poll auth status.
5. Finalize login and extract session cookies/tokens.
6. Save session to local store (`SessionStore`).

---

## 5. Confirmations flow

Main implementation: `ConfirmationService.kt`

- `loadBundles(...)`
  - Calls Steam mobile confirmations endpoint.
  - Groups confirmations by type for UI rendering.

- `respondItem(...)`
  - Single confirmation action (`ajaxop`).

- `respondBundle(...)`
  - Batch action (`multiajaxop` POST form content).

---

## 6. Background checks and notifications

### Components

- `BackgroundSyncScheduler.kt`
- `BackgroundSyncAlarmReceiver.kt`
- `ConfirmationBackgroundWorker.kt`
- `BootCompletedReceiver.kt`

### Runtime behavior

- Settings enables/disables background mode.
- Scheduler creates recurring alarm + immediate worker enqueue.
- Worker iterates imported accounts, loads pending confirmations.
- If pending confirmations > 0 and push enabled -> posts notification.
- Boot/package update receiver reconfigures schedule.

> Timing is best-effort. Android power management (Doze/battery optimization) may delay exact cadence.

---

## 7. Data and storage

- Imported files: app-internal `files/mafiles`
- Session cache: `SharedPreferences` (`msda_sessions`)
- UI/settings: `SharedPreferences` (`msda_ui`)

Security baseline:
- `android:allowBackup="false"`

Recommended future hardening:
- Keystore-backed encryption for sensitive values.
- Optional biometric gate for revealing actions.

---

## 8. Build and run system

### Scripts

- `tools/build-android-package.ps1`
- `tools/run-android-emulator.ps1`
- `tools/launch-from-vs.ps1`

### Behavior

- Build native Android `.so`.
- Assemble/install APK.
- Launch app on connected physical device when present.
- Emulator fallback when no USB device available.

---

## 9. Troubleshooting checklist

1. `adb devices` must show `device`.
2. Ensure `ANDROID_NDK_HOME` is valid.
3. Use supported Java runtime (Java 21 recommended for this setup).
4. If no push notifications:
   - enable background + push in settings,
   - grant notifications permission,
   - disable battery optimization for app.

---

## 10. Release checklist

- [ ] Build `MSDA.AndroidPackage` successfully in Visual Studio.
- [ ] Verify LoginV2 re-login flow on real device.
- [ ] Verify item/bundle confirmation actions.
- [ ] Verify dark/light themes on Hub + Details + Settings.
- [ ] Verify background notification path with app closed.
- [ ] Update `settings_app_version` string when releasing.
