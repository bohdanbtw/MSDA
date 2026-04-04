param(
    [string]$PackageName = "com.msda.android",
    [string]$ActivityName = ".HubActivity",
    [string]$AvdName = "",
    [switch]$StartOnly
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw "adb was not found in PATH. Install Android SDK platform-tools and add it to PATH."
}

if (-not (Get-Command emulator -ErrorAction SilentlyContinue)) {
    throw "emulator tool was not found in PATH. Install Android SDK emulator and add it to PATH."
}

function Wait-ForBoot {
    for ($i = 0; $i -lt 180; $i++) {
        Start-Sleep -Seconds 2
        $boot = (& adb shell getprop sys.boot_completed 2>$null).Trim()
        if ($boot -eq "1") {
            Write-Host "[MSDA] Emulator boot completed."
            return
        }
    }

    throw "Emulator boot did not complete in time."
}

function Ensure-AvdKeyboardEnabled {
    param([string]$Name)

    if ([string]::IsNullOrWhiteSpace($Name)) { return }

    $avdConfig = Join-Path $env:USERPROFILE ".android\avd\$Name.avd\config.ini"
    if (-not (Test-Path $avdConfig)) { return }

    $content = Get-Content $avdConfig -ErrorAction SilentlyContinue
    if (-not $content) { return }

    $updated = $false
    if ($content -notmatch '^hw\.keyboard=') {
        Add-Content -Path $avdConfig -Value "hw.keyboard=yes"
        $updated = $true
    } else {
        $content = $content -replace '^hw\.keyboard=.*$', 'hw.keyboard=yes'
        Set-Content -Path $avdConfig -Value $content
        $updated = $true
    }

    if ($updated) {
        Write-Host "[MSDA] Ensured hardware keyboard is enabled for AVD '$Name'."
    }
}

function Start-EmulatorIfNeeded {
    $adbDevices = & adb devices
    if ($adbDevices -match "emulator-.*\sdevice") {
        Write-Host "[MSDA] Emulator already running."
        Wait-ForBoot
        return
    }

    if ([string]::IsNullOrWhiteSpace($AvdName)) {
        $avds = & emulator -list-avds
        if (-not $avds) {
            throw "No AVD found. Create one in Android Device Manager first."
        }
        $script:AvdName = ($avds | Select-Object -First 1).Trim()
    }

    Ensure-AvdKeyboardEnabled -Name $script:AvdName

    Write-Host "[MSDA] Starting emulator '$script:AvdName'..."
    Start-Process -FilePath "emulator" -ArgumentList "-avd $script:AvdName -gpu swiftshader_indirect -no-snapshot-load -no-boot-anim" | Out-Null

    for ($i = 0; $i -lt 180; $i++) {
        Start-Sleep -Seconds 2
        $devices = & adb devices
        if ($devices -match "emulator-.*\sdevice") {
            Write-Host "[MSDA] Emulator connected."
            Wait-ForBoot
            return
        }
    }

    throw "Emulator did not start in time."
}

function Configure-EmulatorInputAndNavigation {
    Write-Host "[MSDA] Configuring emulator input/navigation..."

    try { & adb shell settings put secure show_ime_with_hard_keyboard 1 | Out-Null } catch {}
    try { & adb shell settings put secure navigation_mode 0 | Out-Null } catch {}
    try { & adb shell settings put secure swipe_up_to_switch_apps_enabled 0 | Out-Null } catch {}
    try { & adb shell settings put secure system_navigation_keys_enabled 1 | Out-Null } catch {}
    try { & adb shell settings put system lock_to_app_enabled 0 | Out-Null } catch {}
    try { & adb shell settings put global policy_control null | Out-Null } catch {}
    try { & adb shell am task lock stop | Out-Null } catch {}
    try { & adb shell pkill com.android.systemui | Out-Null } catch {}
}

Start-EmulatorIfNeeded
Configure-EmulatorInputAndNavigation

if ($StartOnly) {
    Write-Host "[MSDA] Emulator start-only mode complete."
    exit 0
}

Write-Host "[MSDA] Launching app..."
& adb shell am start -n "$PackageName/$PackageName$ActivityName"
