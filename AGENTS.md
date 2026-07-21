# AGENTS.md

## Cursor Cloud specific instructions

MSDA is an Android Steam authenticator. It has two buildable parts:

- **Native C++ core** (`AndroidCppApp/src/msda`, `src/cli`): `.mafile` parsing, account
  management, and Steam Guard 2FA (HMAC-SHA1 TOTP) generation. This is portable and builds
  on Linux. The Android JNI bridge (`src/android/jni_bridge.cpp`) wraps this core.
- **Android app** (`AndroidCppApp/packaging`, Kotlin + Gradle): the shipped product. The
  native `arm64-v8a` library is **prebuilt and committed** at
  `AndroidCppApp/packaging/app/src/main/jniLibs/arm64-v8a/libmsda_android.so`, so the Gradle
  APK build does **not** require the Android NDK.

The repo's own scripts (`run_build.bat`, `tools/*.ps1`, CI) are **Windows-only**; ignore them
on Linux and use the commands below instead.

### Toolchain (installed in the VM snapshot)

Interactive shells get these via `~/.bashrc`. **Non-interactive shells do not**, so export
them before building:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_SDK_ROOT="$HOME/android-sdk"
export ANDROID_HOME="$HOME/android-sdk"
export PATH="$HOME/tools/gradle-8.10.2/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"
```

There is no Gradle wrapper jar in the repo (only `gradlew.bat`); use the system `gradle`
(8.10.2) on `PATH`.

### Build / run the native core + CLI (Linux)

```bash
cd AndroidCppApp
# IMPORTANT: default `c++` is clang and fails with "cannot find -lstdc++"; force g++.
cmake -S . -B out/build/linux-debug -G Ninja -DCMAKE_BUILD_TYPE=Debug \
  -DMSDA_BUILD_ANDROID_BRIDGE=OFF -DCMAKE_CXX_COMPILER=g++
cmake --build out/build/linux-debug          # -> out/build/linux-debug/msda_cli
```

`msda_cli` is an interactive demo: it reads a folder path containing `.mafile` files on
stdin, then an account index. Example: `printf '<folder>\n0\n' | ./out/build/linux-debug/msda_cli`.
The 2FA-code path (`AccountManager::activeCode()`) is exercised through the JNI bridge in the
app; to test it directly on Linux, link a small harness against `libmsda_core.a`.

### Build the Android APK

```bash
cd AndroidCppApp/packaging
gradle assembleDebug --no-daemon
# -> app/build/outputs/apk/debug/MSDA-1.3.0.apk
```

Rebuilding `libmsda_android.so` for arm64 (only needed when native C++ changes must ship in
the APK) requires the **Android NDK**, which is not installed; the committed `.so` is used
otherwise.

### Running the app / testing notes

- The GUI app **cannot be run** in this VM: there is no `/dev/kvm` (no hardware acceleration),
  and only the `arm64-v8a` ABI is built, so the Android emulator is not viable here. Validate
  changes via the native build/CLI, APK build, or a physical device with `adb`.
- There is **no automated test suite and no configured linter** in the repo. The strongest
  local checks are: native build succeeds, `msda_cli` imports a `.mafile`, and
  `gradle assembleDebug` produces the APK.
- Version is centralized in `AndroidCppApp/packaging/gradle.properties`
  (`app.version.name` / `app.version.code`); do not hard-code versions elsewhere.
