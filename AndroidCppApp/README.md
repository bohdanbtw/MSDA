# MSDA (Mobile Steam Desktop Authenticator)

MSDA is an Android-focused Steam authenticator/confirmation app backed by a native C++ core and a Visual Studio-driven packaging workflow.

This project is designed for **Visual Studio first** development (one-click build/install/launch), while the Android app UI is implemented in Kotlin and communicates with the C++ core through JNI.

---

## Features

- Import and manage multiple `.mafile` accounts
- Hub screen with account list and account details navigation
- Steam LoginV2 authentication flow for mobile/session capture
- Active confirmation list with:
  - per-item accept/decline
  - bundle accept/decline
  - item icons
- Background confirmation checks
- Optional push notifications for pending confirmations
- Settings page for theme and background behavior
- Tap to copy current 2FA code

---

## Repository structure

- `AndroidCppApp/src/`
  - Native C++ core and JNI bridge (`jni_bridge.cpp`)
- `AndroidCppApp/packaging/`
  - Android app project
- `AndroidCppApp/packaging/app/src/main/java/com/msda/android/`
  - App UI, auth, confirmations, settings, background workers
- `AndroidCppApp/tools/`
  - Visual Studio launch/build scripts

---

## Visual Studio one-click workflow (recommended)

1. Open `MSDA.slnx` in Visual Studio.
2. Set `MSDA.AndroidPackage` as startup project.
3. Press `F5`.

The scripts will:
- prepare environment,
- build native Android `.so`,
- build/install APK,
- launch app on connected device (USB preferred) or emulator fallback.

---

## Environment requirements

- Visual Studio with C++ + CMake components
- Android SDK (`adb`, `emulator`)
- Android NDK (`ANDROID_NDK_HOME`)
- Java 21 recommended
- Gradle 8.x available (`C:\Tools\Gradle\gradle-8.10.2\bin` in current setup)

---

## Key scripts

- `tools/launch-from-vs.ps1`
  - Main one-click flow for Visual Studio
- `tools/build-android-package.ps1`
  - Native build + APK assemble/install
- `tools/run-android-emulator.ps1`
  - Emulator start/config + app launch

---

## App screens

### Hub (`HubActivity`)
- Account list
- Add/import account
- Open account details
- Open settings

### Account details (`MainActivity`)
- Current 2FA code and timer
- Confirmation list and actions
- Re-login action menu

### Settings (`SettingsActivity`)
- Theme: System / Light / Dark
- Background confirmation checks (on/off)
- Push notifications for confirmations (on/off)

---

## Background checks and notifications

Background check behavior is controlled in Settings:
- **Allow background confirmations check**
- **Enable push notifications for confirmations**

Implementation uses:
- `AlarmManager` trigger loop
- `WorkManager` worker execution
- boot/package-replaced receiver to restore scheduling

> Android may still throttle exact timing under Doze/battery optimization.

---

## Security notes

Current project hardening includes:
- `android:allowBackup="false"`
- internal app storage for imported files/sessions

If you need stronger at-rest protection, plan for:
- Android Keystore-based encryption for sensitive session/mafile data,
- biometric gate for decrypt operations,
- root-detection/attestation strategy.

---

## Troubleshooting

- If app launches emulator instead of phone: ensure `adb devices` shows your USB device as `device`.
- If Gradle fails with Java class-version errors: pin Java runtime to supported version (Java 21 recommended).
- If confirmations require re-login: refresh session from account detail action menu.

---

## Version

Current app version footer in Settings: **1.0.0**
