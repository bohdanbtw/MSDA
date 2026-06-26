param(
    [string]$Password = "",
    [string]$Alias = "upload",
    [int]$ValidityDays = 10000
)

$ErrorActionPreference = "Stop"

$packagingRoot = Join-Path (Split-Path -Parent $PSScriptRoot) "packaging"
$keystoreFile = Join-Path $packagingRoot "release.jks"
$propsFile = Join-Path $packagingRoot "keystore.properties"

function Find-JavaHome {
    $candidates = @(
        $env:JAVA_HOME,
        "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot",
        "C:\Program Files\Android\openjdk\jdk-21.0.8"
    )
    $candidates += Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -Filter "jdk-*" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName
    $candidates += Get-ChildItem "C:\Program Files\Android\openjdk" -Directory -Filter "jdk-*" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName

    foreach ($jdk in ($candidates | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique)) {
        if (Test-Path (Join-Path $jdk "bin\keytool.exe")) {
            return $jdk
        }
    }

    throw "JDK with keytool not found. Install Temurin 21 or set JAVA_HOME."
}

function New-RandomPassword([int]$Length = 20) {
    $chars = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789!@#%_-"
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $bytes = New-Object byte[] $Length
    $rng.GetBytes($bytes)
    return -join ($bytes | ForEach-Object { $chars[$_ % $chars.Length] })
}

$javaHome = Find-JavaHome
$keytool = Join-Path $javaHome "bin\keytool.exe"

if ([string]::IsNullOrWhiteSpace($Password)) {
    $Password = New-RandomPassword
    Write-Host "[MSDA] Generated a new signing password."
}

if (Test-Path $keystoreFile) {
    Write-Host "[MSDA] Keystore already exists: $keystoreFile"
    Write-Host "[MSDA] Delete it first if you want to generate a new one."
} else {
    Write-Host "[MSDA] Creating release keystore..."
    $dname = "CN=MSDA, OU=Mobile, O=bohdanbtw, L=Unknown, ST=Unknown, C=UA"
    & $keytool -genkeypair `
        -v `
        -keystore $keystoreFile `
        -alias $Alias `
        -keyalg RSA `
        -keysize 2048 `
        -validity $ValidityDays `
        -storepass $Password `
        -keypass $Password `
        -dname $dname

    if ($LASTEXITCODE -ne 0) {
        throw "keytool failed with exit code $LASTEXITCODE"
    }

    Write-Host "[MSDA] Keystore created: $keystoreFile"
}

@"
storeFile=release.jks
storePassword=$Password
keyAlias=$Alias
keyPassword=$Password
"@ | Set-Content -Path $propsFile -Encoding UTF8

Write-Host ""
Write-Host "=== Release signing ready ===" -ForegroundColor Green
Write-Host "Keystore : $keystoreFile"
Write-Host "Props    : $propsFile"
Write-Host "Alias    : $Alias"
Write-Host "Password : $Password"
Write-Host ""
Write-Host "Save this password in your password manager. It is required for every release build."
Write-Host "These files are gitignored and will NOT be committed."
