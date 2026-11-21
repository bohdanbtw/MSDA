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

## Device / ABI support (64-bit only)

The APK ships a single native library, `libmsda_android.so`, built for the
**arm64-v8a** ABI only. `app/build.gradle` declares this explicitly via
`ndk.abiFilters 'arm64-v8a'`, so the APK contains only `lib/arm64-v8a/`.

Consequences:

- **MSDA requires a 64-bit ARM (ARM64) Android device.** Although `minSdk` is
  26 (Android 8.0), 32-bit-only ARM devices (`armeabi-v7a`) cannot install or
  run the app.
- There is no 32-bit `.so` in the repository, and building one requires the
  Android NDK. Until a 32-bit native library is produced and added to
  `app/src/main/jniLibs/`, `abiFilters` must stay limited to `arm64-v8a`.
- x86 / x86_64 devices and emulators are not supported by the shipped APK
  unless they provide ARM64 translation.

---

## Release build configuration

- `compileSdk` / `targetSdk` are set to **35** (the newest platform available
  in the offline build environment), meeting Google Play's 35+ target
  requirement for new releases.
- Release builds enable R8 minification (`minifyEnabled true`) and resource
  shrinking (`shrinkResources true`). Keep rules in `app/proguard-rules.pro`
  protect the name-based JNI bridge (`NativeBridge`) and the data/model DTOs;
  do not remove them or release builds can fail with `UnsatisfiedLinkError`.
- After changing SDK levels, re-test runtime permissions, alarms, and
  notifications on a device, plus a release smoke test (import `.mafile`,
  generate a 2FA code, load confirmations).

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
