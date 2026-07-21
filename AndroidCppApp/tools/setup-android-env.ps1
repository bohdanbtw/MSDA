# MSDA Android dev environment setup (run in PowerShell as your user)
# You will be prompted to type "y" to accept SDK licenses.

$ErrorActionPreference = "Stop"

$sdkRoot = "$env:LOCALAPPDATA\Android\Sdk"
$javaHome = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
$gradleHome = "C:\Tools\Gradle\gradle-8.10.2"
$avdName = "MSDA_API30"

if (-not (Test-Path $javaHome)) {
    throw "Java 21 not found at $javaHome. Install Temurin 21 first."
}

if (-not (Test-Path "$sdkRoot\cmdline-tools\latest\bin\sdkmanager.bat")) {
    throw "Android cmdline-tools not found. Re-run Android Studio setup or install cmdline-tools."
}

$env:JAVA_HOME = $javaHome
$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_SDK_ROOT = $sdkRoot
$env:ANDROID_NDK_HOME = ""
$env:PATH = @(
    "$javaHome\bin",
    "$sdkRoot\cmdline-tools\latest\bin",
    "$sdkRoot\platform-tools",
    "$sdkRoot\emulator",
    "$gradleHome\bin",
    $env:PATH
) -join ";"

$sdkmanager = "$sdkRoot\cmdline-tools\latest\bin\sdkmanager.bat"
$avdmanager = "$sdkRoot\cmdline-tools\latest\bin\avdmanager.bat"

Write-Host ""
Write-Host "=== STEP 1/4: Accept Android SDK licenses ===" -ForegroundColor Cyan
Write-Host "Type 'y' and press Enter for EACH license prompt."
Write-Host ""
& $sdkmanager --licenses
if ($LASTEXITCODE -ne 0) {
    throw "License acceptance failed. Exit code: $LASTEXITCODE"
}

Write-Host ""
Write-Host "=== STEP 2/4: Install SDK packages (may take 10-20 min) ===" -ForegroundColor Cyan
& $sdkmanager `
    "platform-tools" `
    "emulator" `
    "platforms;android-34" `
    "platforms;android-30" `
    "build-tools;34.0.0" `
    "ndk;27.2.12479018" `
    "system-images;android-30;google_apis;x86_64"
if ($LASTEXITCODE -ne 0) {
    throw "sdkmanager package install failed. Exit code: $LASTEXITCODE"
}

$ndk = Get-ChildItem "$sdkRoot\ndk" -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1
if ($ndk) {
    $env:ANDROID_NDK_HOME = $ndk.FullName
}

Write-Host ""
Write-Host "=== STEP 3/4: Create emulator AVD '$avdName' ===" -ForegroundColor Cyan
$existing = & $avdmanager list avd 2>$null
if ($existing -match [regex]::Escape($avdName)) {
    Write-Host "AVD '$avdName' already exists, skipping."
} else {
    echo no | & $avdmanager create avd `
        -n $avdName `
        -k "system-images;android-30;google_apis;x86_64" `
        -d "pixel_4"
    if ($LASTEXITCODE -ne 0) {
        throw "AVD creation failed. Exit code: $LASTEXITCODE"
    }
}

Write-Host ""
Write-Host "=== STEP 4/4: Save user environment variables ===" -ForegroundColor Cyan
[Environment]::SetEnvironmentVariable("JAVA_HOME", $javaHome, "User")
[Environment]::SetEnvironmentVariable("ANDROID_HOME", $sdkRoot, "User")
[Environment]::SetEnvironmentVariable("ANDROID_SDK_ROOT", $sdkRoot, "User")
if ($env:ANDROID_NDK_HOME) {
    [Environment]::SetEnvironmentVariable("ANDROID_NDK_HOME", $env:ANDROID_NDK_HOME, "User")
}

$userPath = [Environment]::GetEnvironmentVariable("Path", "User")
$toAdd = @(
    "$javaHome\bin",
    "$sdkRoot\platform-tools",
    "$sdkRoot\emulator",
    "$sdkRoot\cmdline-tools\latest\bin",
    "$gradleHome\bin"
)
foreach ($p in $toAdd) {
    if ($userPath -notlike "*$p*") {
        $userPath = "$p;$userPath"
    }
}
[Environment]::SetEnvironmentVariable("Path", $userPath, "User")

Write-Host ""
Write-Host "=== DONE ===" -ForegroundColor Green
Write-Host "JAVA_HOME=$javaHome"
Write-Host "ANDROID_HOME=$sdkRoot"
Write-Host "ANDROID_NDK_HOME=$env:ANDROID_NDK_HOME"
Write-Host ""
Write-Host "Next steps:"
Write-Host "  1. Close and reopen Visual Studio / terminals (to pick up PATH)."
Write-Host "  2. Open MSDA.slnx in Visual Studio."
Write-Host "  3. Set startup project: MSDA.AndroidPackage"
Write-Host "  4. Press F5 (emulator MSDA_API30 starts automatically if no phone connected)."
Write-Host ""
Write-Host "Verify tools:"
& adb version
& emulator -list-avds
