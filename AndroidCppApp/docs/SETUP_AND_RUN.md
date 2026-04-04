# Setup and Run (Visual Studio)

## Prerequisites

- Visual Studio (C++ + CMake)
- Android SDK + platform tools (`adb`, `emulator`)
- Android NDK (`ANDROID_NDK_HOME`)
- Java 21
- Gradle 8.x

## Run flow

1. Open solution in Visual Studio.
2. Set `MSDA.AndroidPackage` startup project.
3. Press `F5`.

The script chain:
- builds native library
- assembles/install APK
- launches app on USB device (preferred) or emulator

## Useful scripts

- `tools/launch-from-vs.ps1`
- `tools/build-android-package.ps1`
- `tools/run-android-emulator.ps1`

## App background checks

Enable in Settings:
- Background confirmations check
- Push notifications

If notifications do not appear:
- verify Android notification permission
- disable battery optimization for app
- ensure account has valid session
