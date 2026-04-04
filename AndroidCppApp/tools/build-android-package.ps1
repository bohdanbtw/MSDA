param(
    [string]$Configuration = "Debug"
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$packagingRoot = Join-Path $repoRoot "packaging"
$jniLibDir = Join-Path $packagingRoot "app\src\main\jniLibs\arm64-v8a"

if ([string]::IsNullOrWhiteSpace($env:ANDROID_NDK_HOME)) {
    throw "ANDROID_NDK_HOME is not set. Configure it in system environment variables to your Android NDK path."
}

$toolchainPath = Join-Path $env:ANDROID_NDK_HOME "build\cmake\android.toolchain.cmake"
if (-not (Test-Path $toolchainPath)) {
    throw "Android toolchain not found at '$toolchainPath'. Verify ANDROID_NDK_HOME points to a valid NDK."
}

$cmakeFromPath = Get-Command cmake -ErrorAction SilentlyContinue
if (-not $cmakeFromPath) {
    $vsCmake = "C:\Program Files\Microsoft Visual Studio\18\Community\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe"
    if (Test-Path $vsCmake) {
        $env:PATH = (Split-Path $vsCmake -Parent) + ";" + $env:PATH
    }
}

if (-not (Get-Command cmake -ErrorAction SilentlyContinue)) {
    throw "CMake was not found. Install Visual Studio CMake components or add cmake to PATH."
}

$ninjaFromPath = Get-Command ninja -ErrorAction SilentlyContinue
if (-not $ninjaFromPath) {
    $vsNinja = "C:\Program Files\Microsoft Visual Studio\18\Community\Common7\IDE\CommonExtensions\Microsoft\CMake\Ninja\ninja.exe"
    if (Test-Path $vsNinja) {
        $env:PATH = (Split-Path $vsNinja -Parent) + ";" + $env:PATH
    }
}

if (-not (Get-Command ninja -ErrorAction SilentlyContinue)) {
    throw "Ninja was not found. Install Ninja or Visual Studio CMake components."
}

$gradleCmd = $null
if (Test-Path (Join-Path $packagingRoot "gradlew.bat")) {
    $gradleCmd = Join-Path $packagingRoot "gradlew.bat"
} elseif (Get-Command gradle -ErrorAction SilentlyContinue) {
    $gradleCmd = "gradle"
}

if (-not $gradleCmd) {
    throw "Gradle was not found. Install Gradle or add Gradle wrapper into AndroidCppApp\packaging."
}

Write-Host "[MSDA] Building Android native library..."
cmake --preset android-arm64-debug -S $repoRoot
cmake --build (Join-Path $repoRoot "out\build\android-arm64-debug") --target msda_android

$builtLib = Get-ChildItem -Path (Join-Path $repoRoot "out\build\android-arm64-debug") -Filter "libmsda_android.so" -Recurse | Select-Object -First 1
if (-not $builtLib) {
    throw "libmsda_android.so not found after native build."
}

New-Item -ItemType Directory -Path $jniLibDir -Force | Out-Null
Copy-Item $builtLib.FullName (Join-Path $jniLibDir "libmsda_android.so") -Force
Write-Host "[MSDA] Native library copied to packaging app."

Push-Location $packagingRoot
try {
    $hardPinnedJdk = "C:\Program Files\Android\openjdk\jdk-21.0.8"
    $javaMajor = 0

    if (Test-Path (Join-Path $hardPinnedJdk "bin\java.exe")) {
        $env:JAVA_HOME = $hardPinnedJdk
        $env:PATH = "$(Join-Path $hardPinnedJdk 'bin');$env:PATH"
        $javaMajor = 21
    }

    $preferredJdks = @(
        $env:JAVA_HOME,
        "C:\Program Files\Android\openjdk\jdk-21.0.8",
        "C:\Program Files\Android\openjdk\jdk-21",
        "C:\Program Files\Java\jdk-21"
    )

    $discovered = @()
    $discovered += Get-ChildItem "C:\Program Files\Android\openjdk" -Directory -Filter "jdk-*" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName
    $discovered += Get-ChildItem "C:\Program Files\Java" -Directory -Filter "jdk-*" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName
    $preferredJdks = @($preferredJdks + $discovered | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | ForEach-Object { $_.TrimEnd('\\') } | Select-Object -Unique)

    function Get-JavaMajor([string]$versionText) {
        if ($versionText -match '"(?<v>[0-9]+)(\.[0-9]+)?') {
            return [int]$matches['v']
        }
        return 0
    }

    if ($javaMajor -eq 0) {
        foreach ($jdk in $preferredJdks) {
            $javaExe = Join-Path $jdk "bin\java.exe"
            if (-not (Test-Path $javaExe)) { continue }

            $ErrorActionPreference = "SilentlyContinue"
            $javaVersion = & $javaExe -version 2>&1 | Out-String
            $ErrorActionPreference = "Stop"
            $major = Get-JavaMajor $javaVersion

            if ($major -ge 11 -and $major -le 23) {
                $javaMajor = $major
                $env:JAVA_HOME = $jdk
                $env:PATH = "$(Join-Path $jdk 'bin');$env:PATH"
                break
            }
        }
    }

    if ($javaMajor -lt 11 -or $javaMajor -gt 23) {
        throw "Java version incompatibility: Gradle requires Java 11-23. Could not find compatible JDK. JAVA_HOME='$env:JAVA_HOME'"
    }

    $env:GRADLE_USER_HOME = Join-Path $repoRoot "out\gradle-home"
    New-Item -ItemType Directory -Path $env:GRADLE_USER_HOME -Force | Out-Null

    $gradleTasks = @(':app:assembleDebug')
    $hasConnectedDevice = $false

    if (Get-Command adb -ErrorAction SilentlyContinue) {
        $adbDevices = & adb devices 2>$null
        $deviceLines = $adbDevices | Where-Object { $_ -match "\sdevice$" -and $_ -notmatch "List of devices" }
        if ($deviceLines) {
            $hasConnectedDevice = $true
        }
    }

    if ($hasConnectedDevice) {
        $gradleTasks += ':app:installDebug'
    } else {
        Write-Host "[MSDA] No connected Android device/emulator detected. Skipping :app:installDebug."
    }

    $gradleJvmArg = "-Dorg.gradle.java.home=$env:JAVA_HOME"

    if ($gradleCmd -eq "gradle") {
        & gradle --no-daemon $gradleJvmArg '-Pkotlin.incremental=false' @gradleTasks
    } else {
        & $gradleCmd --no-daemon $gradleJvmArg '-Pkotlin.incremental=false' @gradleTasks
    }

    if ($LASTEXITCODE -ne 0) {
        throw "Gradle failed with exit code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}

if ($hasConnectedDevice) {
    Write-Host "[MSDA] Android package built and installed."
} else {
    Write-Host "[MSDA] Android package built (not installed)."
}
