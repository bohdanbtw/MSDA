# Fix `Join-Path` error in the Android build script

The `tools/build-android-package.ps1` script uses `Join-Path` with an unquoted
environment variable, which fails when `ANDROID_NDK_HOME` is empty or contains
spaces.

## Quick fix

1. Open `tools/build-android-package.ps1` in a text editor.

2. Find the line that currently looks like this (around line 15):

   ```powershell
   $toolchainPath = Join-Path $env:ANDROID_NDK_HOME "build\cmake\android.toolchain.cmake"
   ```

3. Replace it with:

   ```powershell
   $toolchainPath = Join-Path "$env:ANDROID_NDK_HOME" "build\cmake\android.toolchain.cmake"
   ```

   The double quotes around `$env:ANDROID_NDK_HOME` prevent PowerShell from
   splitting the argument on spaces and help when the path is empty.

4. If `ANDROID_NDK_HOME` is not set at all, add a check before the `Join-Path`
   line:

   ```powershell
   if (-not $env:ANDROID_NDK_HOME) {
       Write-Error "ANDROID_NDK_HOME environment variable is not set."
       exit 1
   }
   ```

   Then set the variable in your system environment, or temporarily in the
   terminal:

   ```cmd
   set ANDROID_NDK_HOME=C:\Path\To\Your\NDK
   .\run_build.bat
   ```
