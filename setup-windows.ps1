$ErrorActionPreference = 'Stop'
$projectDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$toolsDir = Join-Path $projectDir '.tools'
$sdkDir = Join-Path $toolsDir 'android-sdk'
$cmdlineDir = Join-Path $sdkDir 'cmdline-tools\latest'
$gradleHome = Join-Path $toolsDir 'gradle-8.9'
New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null

function Get-Download($url, $destination) {
    & curl.exe --location --fail --retry 5 --retry-all-errors --retry-delay 3 --connect-timeout 30 --continue-at - --output $destination $url
    if ($LASTEXITCODE -ne 0) { throw "Download failed after retries: $url" }
}

if (-not (Test-Path (Join-Path $cmdlineDir 'bin\sdkmanager.bat'))) {
    $zip = Join-Path $toolsDir 'commandlinetools-win.zip'
    Get-Download 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip' $zip
    $actual = (Get-FileHash $zip -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne '90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a') { throw 'Android command line tools checksum did not match the official checksum.' }
    $staging = Join-Path $toolsDir 'sdk-staging'
    Expand-Archive -Path $zip -DestinationPath $staging -Force
    New-Item -ItemType Directory -Force -Path (Split-Path $cmdlineDir -Parent) | Out-Null
    Move-Item (Join-Path $staging 'cmdline-tools') $cmdlineDir -Force
    Remove-Item $staging -Recurse -Force
    Remove-Item $zip -Force
}

if (-not (Test-Path (Join-Path $gradleHome 'bin\gradle.bat'))) {
    $zip = Join-Path $toolsDir 'gradle-8.9-bin.zip'
    Get-Download 'https://services.gradle.org/distributions/gradle-8.9-bin.zip' $zip
    Expand-Archive -Path $zip -DestinationPath $toolsDir -Force
    Remove-Item $zip -Force
}

$env:ANDROID_HOME = $sdkDir
$env:ANDROID_SDK_ROOT = $sdkDir
$sdkManager = Join-Path $cmdlineDir 'bin\sdkmanager.bat'
& $sdkManager --sdk_root=$sdkDir --licenses
if ($LASTEXITCODE -ne 0) { throw 'Android SDK licenses were not accepted.' }
& $sdkManager --sdk_root=$sdkDir 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0'
if ($LASTEXITCODE -ne 0) { throw 'Android SDK package installation failed.' }
$env:PATH = (Join-Path $sdkDir 'platform-tools') + ';' + $env:PATH
$env:ANDROID_HOME = $sdkDir
& (Join-Path $gradleHome 'bin\gradle.bat') --no-daemon assembleDebug
if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed; review the error output above.' }
Write-Host "APK created: $(Join-Path $projectDir 'app\build\outputs\apk\debug\app-debug.apk')"
