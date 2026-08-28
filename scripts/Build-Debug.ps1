param()

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $projectRoot 'gradlew.bat'
$sourceApk = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'
$artifactDirectory = Join-Path $projectRoot 'artifacts\apk'
$artifactApk = Join-Path $artifactDirectory 'Viewer-Android-arm64-debug.apk'

& $gradle :app:assembleDebug
if ($LASTEXITCODE -ne 0) {
    throw "Android Debug 构建失败，退出代码：$LASTEXITCODE"
}

New-Item -ItemType Directory -Force -Path $artifactDirectory | Out-Null
Copy-Item -LiteralPath $sourceApk -Destination $artifactApk -Force
Write-Output "APK: $artifactApk"

