# Copilot Instructions

## Project Guidelines
- User prefers solutions that work in Visual Studio instead of Android Studio.
- User wants one-click Visual Studio Play/F5 behavior that builds APK, starts emulator, and launches app without manual commands.
- For Android login, prioritize Steam mobile-specific auth/session flow (SteamMobile/MobileSteamClient) over desktop-oriented login behavior, as they may differ. For Steam QR/login implementation, ensure it targets Steam mobile auth/session behavior; desktop-oriented request patterns are not acceptable for this project.
- Do not use Nebula or NebulaAuth in file names or code; keep MSDA naming only.

## Versioning Workflow
- All version information is centralized in `AndroidCppApp/packaging/gradle.properties` with two properties:
  - `app.version.name=X.X.X` (semantic version like 1.1.0)
  - `app.version.code=X` (integer, incremented for each release)
- The `build.gradle` automatically reads these values:
  - `versionCode` and `versionName` use `gradle.properties` values.
  - APK filename is auto-generated as `MSDA-{versionName}.apk`.
  - UI version string in settings is auto-generated via `resValue` during compilation.
- To update the version: Only modify `gradle.properties`, rebuild with F5, and everything else updates automatically. Never manually edit version in `build.gradle` or `strings.xml`.