param(
    [string]$AvdName = ""
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$sdkPath = "C:\Users\$env:USERNAME\AppData\Local\Android\Sdk"
$ndkBase = Join-Path $sdkPath "ndk"
$gradleBin = "C:\Tools\Gradle\gradle-8.10.2\bin"
$vsCmakeBin = "C:\Program Files\Microsoft Visual Studio\18\Community\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin"
$vsNinjaBin = "C:\Program Files\Microsoft Visual Studio\18\Community\Common7\IDE\CommonExtensions\Microsoft\CMake\Ninja"

if (Test-Path $sdkPath) {
    if ([string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) { $env:ANDROID_HOME = $sdkPath }
    if ([string]::IsNullOrWhiteSpace($env:ANDROID_SDK_ROOT)) { $env:ANDROID_SDK_ROOT = $sdkPath }

    $pathParts = @(
        (Join-Path $sdkPath "platform-tools"),
        (Join-Path $sdkPath "emulator"),
        (Join-Path $sdkPath "cmdline-tools\latest\bin")
    )

    foreach ($p in $pathParts) {
        if ((Test-Path $p) -and ($env:PATH -notlike "*$p*")) {
            $env:PATH = "$p;$env:PATH"
        }
    }

    if (Test-Path $ndkBase) {
        $ndk = Get-ChildItem $ndkBase -Directory | Sort-Object Name -Descending | Select-Object -First 1
        if ($ndk -and [string]::IsNullOrWhiteSpace($env:ANDROID_NDK_HOME)) {
            $env:ANDROID_NDK_HOME = $ndk.FullName
        }
    }
}

if ((Test-Path $gradleBin) -and ($env:PATH -notlike "*$gradleBin*")) {
    $env:PATH = "$gradleBin;$env:PATH"
}

if ((Test-Path $vsCmakeBin) -and ($env:PATH -notlike "*$vsCmakeBin*")) {
    $env:PATH = "$vsCmakeBin;$env:PATH"
}

if ((Test-Path $vsNinjaBin) -and ($env:PATH -notlike "*$vsNinjaBin*")) {
    $env:PATH = "$vsNinjaBin;$env:PATH"
}

$openJdk = "C:\Program Files\Android\openjdk\jdk-21.0.8"
if ((Test-Path (Join-Path $openJdk "bin\java.exe")) -and [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    $env:JAVA_HOME = $openJdk
    if ($env:PATH -notlike "*$openJdk\bin*") {
        $env:PATH = "$openJdk\bin;$env:PATH"
    }
}

function Get-PhysicalDeviceSerial {
    if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
        return $null
    }

    $lines = & adb devices
    foreach ($line in $lines) {
        $trim = $line.Trim()
        if ([string]::IsNullOrWhiteSpace($trim)) { continue }
        if ($trim -like "List of devices*") { continue }
        if ($trim -match "^(\S+)\s+device$") {
            $serial = $matches[1]
            if ($serial -notlike "emulator-*") {
                return $serial
            }
        }
    }

    return $null
}

$physicalDevice = Get-PhysicalDeviceSerial

if ([string]::IsNullOrWhiteSpace($physicalDevice)) {
    if ([string]::IsNullOrWhiteSpace($AvdName)) {
        & (Join-Path $PSScriptRoot "run-android-emulator.ps1") -StartOnly
    } else {
        & (Join-Path $PSScriptRoot "run-android-emulator.ps1") -AvdName $AvdName -StartOnly
    }
} else {
    Write-Host "[MSDA] Physical device detected: $physicalDevice"
}

& (Join-Path $PSScriptRoot "build-android-package.ps1")

if ([string]::IsNullOrWhiteSpace($physicalDevice)) {
    if ([string]::IsNullOrWhiteSpace($AvdName)) {
        & (Join-Path $PSScriptRoot "run-android-emulator.ps1")
    } else {
        & (Join-Path $PSScriptRoot "run-android-emulator.ps1") -AvdName $AvdName
    }
} else {
    Write-Host "[MSDA] Launching app on physical device..."
    & adb -s $physicalDevice shell am start -n "com.msda.android/com.msda.android.HubActivity"
}
