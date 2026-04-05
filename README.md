# MSDA

MSDA (Mobile Steam Desktop Authenticator) is a Visual Studio-first Android authenticator and confirmation manager for Steam mobile accounts.

This repository contains:
- native C++ core (`AndroidCppApp/src`)
- Android app UI (Kotlin) (`AndroidCppApp/packaging/app/src/main/java/com/msda/android`)
- build/launch automation scripts for one-click Visual Studio flow (`AndroidCppApp/tools`)

## Quick start

1. Open `MSDA.slnx` in Visual Studio.
2. Set `MSDA.AndroidPackage` as startup project.
3. Press `F5`.

The workflow builds native libs, assembles/installs APK, and launches on USB device (preferred) or emulator fallback.

## Documentation

- Main project documentation: `AndroidCppApp/README.md`
- Setup and run guide: `AndroidCppApp/docs/SETUP_AND_RUN.md`
- Architecture and technical deep dive: `AndroidCppApp/docs/ARCHITECTURE.md`
- GitHub publishing notes: `AndroidCppApp/docs/GITHUB_PUBLISH.md`

## Key capabilities

- Multi-account `.mafile` import and account hub
- Steam LoginV2 flow
- Confirmation actions (single and batch)
- Background checks + optional notifications
- Theme and behavior settings
