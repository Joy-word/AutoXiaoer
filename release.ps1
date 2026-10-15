[CmdletBinding()]
param(
    [string]$KeyAlias
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Read-SigningPassword {
    param([string]$Prompt)

    $securePassword = Read-Host $Prompt -AsSecureString
    return [System.Net.NetworkCredential]::new('', $securePassword).Password
}

$projectRoot = $PSScriptRoot
$buildFile = Join-Path $projectRoot 'app\build.gradle.kts'
$keystoreFile = Join-Path $projectRoot 'app\release.keystore'
$wrapperFile = Join-Path $projectRoot 'gradlew.bat'

foreach ($requiredFile in @($buildFile, $keystoreFile, $wrapperFile)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Required file not found: $requiredFile"
    }
}

$originalBytes = [System.IO.File]::ReadAllBytes($buildFile)
$originalText = [System.IO.File]::ReadAllText($buildFile)
$codePattern = [regex]'(?m)^(\s*versionCode\s*=\s*)(\d+)(\s*(?://[^\r\n]*)?\r?)$'
$namePattern = [regex]'(?m)^(\s*versionName\s*=\s*")(\d+)\.(\d+)\.(\d+)("\s*(?://[^\r\n]*)?\r?)$'
$codeMatches = $codePattern.Matches($originalText)
$nameMatches = $namePattern.Matches($originalText)
if ($codeMatches.Count -ne 1 -or $nameMatches.Count -ne 1) {
    throw 'Expected exactly one versionCode and one numeric major.minor.patch versionName.'
}

$nextCode = [long]::Parse($codeMatches[0].Groups[2].Value) + 1
if ($nextCode -gt 2100000000) {
    throw 'versionCode exceeds the Android publishing limit.'
}
$nextPatch = [long]::Parse($nameMatches[0].Groups[4].Value) + 1
$nextName = '{0}.{1}.{2}' -f $nameMatches[0].Groups[2].Value, $nameMatches[0].Groups[3].Value, $nextPatch
$updatedText = $codePattern.Replace($originalText, { param($match) $match.Groups[1].Value + $nextCode + $match.Groups[3].Value })
$updatedText = $namePattern.Replace($updatedText, { param($match) $match.Groups[1].Value + $nextName + $match.Groups[5].Value })

$environmentNames = @('KEYSTORE_PASSWORD', 'KEY_PASSWORD', 'KEY_ALIAS')
$previousEnvironment = @{}
foreach ($name in $environmentNames) {
    $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

$versionWritten = $false
$buildSucceeded = $false
try {
    if ([string]::IsNullOrWhiteSpace($KeyAlias)) {
        $KeyAlias = $env:KEY_ALIAS
    }
    if ([string]::IsNullOrWhiteSpace($KeyAlias)) {
        $KeyAlias = Read-Host 'Key alias (Enter for autoxiaoer)'
        if ([string]::IsNullOrWhiteSpace($KeyAlias)) {
            $KeyAlias = 'autoxiaoer'
        }
    }
    $env:KEY_ALIAS = $KeyAlias

    if ([string]::IsNullOrEmpty($env:KEYSTORE_PASSWORD)) {
        $env:KEYSTORE_PASSWORD = Read-SigningPassword 'Keystore password'
    }
    if ([string]::IsNullOrEmpty($env:KEYSTORE_PASSWORD)) {
        throw 'Keystore password must not be empty.'
    }
    if ([string]::IsNullOrEmpty($env:KEY_PASSWORD)) {
        $env:KEY_PASSWORD = Read-SigningPassword 'Key password (Enter to use keystore password)'
        if ([string]::IsNullOrEmpty($env:KEY_PASSWORD)) {
            $env:KEY_PASSWORD = $env:KEYSTORE_PASSWORD
        }
    }

    Write-Host "Building Release: versionCode=$nextCode, versionName=$nextName"
    $versionWritten = $true
    [System.IO.File]::WriteAllText($buildFile, $updatedText, [System.Text.UTF8Encoding]::new($false))

    Push-Location -LiteralPath $projectRoot
    try {
        & $wrapperFile ':app:assembleRelease' '--no-daemon' '--no-configuration-cache'
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle failed with exit code $LASTEXITCODE."
        }
    }
    finally {
        Pop-Location
    }

    $apkFile = Join-Path $projectRoot "app\build\outputs\apk\release\XiaoEr-$nextName-release.apk"
    if (-not (Test-Path -LiteralPath $apkFile -PathType Leaf)) {
        throw "Gradle completed but the expected APK was not found: $apkFile"
    }
    $buildSucceeded = $true
    Write-Host "Release APK: $apkFile"
}
finally {
    if ($versionWritten -and -not $buildSucceeded) {
        [System.IO.File]::WriteAllBytes($buildFile, $originalBytes)
        Write-Warning 'Build did not complete; original version configuration restored.'
    }
    foreach ($name in $environmentNames) {
        [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], 'Process')
    }
}
