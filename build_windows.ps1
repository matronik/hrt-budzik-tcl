# Run on Windows with Android SDK installed. Downloads official Gradle only if needed.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$Sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
if (-not (Test-Path $Sdk)) { throw "Android SDK missing. Install Android Studio / SDK Platform 35. Looked in: $Sdk" }
$sdkForward = $Sdk.Replace('\','/')
"sdk.dir=$sdkForward" | Set-Content -Path 'local.properties' -Encoding ascii
if (-not $env:JAVA_HOME) {
    $studioJava = 'C:\Program Files\Android\Android Studio\jbr'
    if (Test-Path (Join-Path $studioJava 'bin\java.exe')) { $env:JAVA_HOME = $studioJava }
}
$gradleDir = Join-Path $PSScriptRoot '.gradle-dist'
$gradleBat = Join-Path $gradleDir 'gradle-8.9\bin\gradle.bat'
if (-not (Test-Path $gradleBat)) {
    New-Item -ItemType Directory -Force -Path $gradleDir | Out-Null
    $zip = Join-Path $gradleDir 'gradle-8.9-bin.zip'
    Write-Host 'Downloading official Gradle 8.9 from services.gradle.org ...'
    Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-8.9-bin.zip' -OutFile $zip
    Expand-Archive -Path $zip -DestinationPath $gradleDir -Force
}
Write-Host 'Building debug APK ...'
& $gradleBat --no-daemon :app:assembleDebug
if ($LASTEXITCODE -ne 0) { throw "Gradle exit code: $LASTEXITCODE" }
$apk = Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $apk)) { throw "Missing APK: $apk" }
Copy-Item $apk (Join-Path $PSScriptRoot 'HRT_Budzik_TCL_debug.apk') -Force
Write-Host "DONE: $(Join-Path $PSScriptRoot 'HRT_Budzik_TCL_debug.apk')"
