param(
    [string]$SigningProperties = (Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) 'signing\viewer-android-signing.properties'),
    [switch]$SkipChecks
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $projectRoot 'gradlew.bat'
$sourceApk = Join-Path $projectRoot 'app\build\outputs\apk\release\app-release.apk'
$releaseDirectory = Join-Path $projectRoot 'artifacts\release\1.0.0'
$releaseApk = Join-Path $releaseDirectory 'Viewer-Android-arm64-v1.0.0.apk'

if (-not (Test-Path -LiteralPath $SigningProperties -PathType Leaf)) {
    throw "未找到签名配置：$SigningProperties"
}

$signingArgument = "-Pviewer.signing.properties=$SigningProperties"
$tasks = if ($SkipChecks) {
    @(':app:assembleRelease')
} else {
    @(':app:lintRelease', ':app:testDebugUnitTest', ':app:assembleRelease')
}

& $gradle $signingArgument @tasks
if ($LASTEXITCODE -ne 0) {
    throw "Android Release 构建失败，退出代码：$LASTEXITCODE"
}

New-Item -ItemType Directory -Force -Path $releaseDirectory | Out-Null
Copy-Item -LiteralPath $sourceApk -Destination $releaseApk -Force
Copy-Item -LiteralPath (Join-Path $projectRoot '使用说明.md') -Destination $releaseDirectory -Force
Copy-Item -LiteralPath (Join-Path $projectRoot '开发文档.md') -Destination $releaseDirectory -Force
Copy-Item -LiteralPath (Join-Path $projectRoot 'THIRD-PARTY-NOTICES.md') -Destination $releaseDirectory -Force

$hash = Get-FileHash -LiteralPath $releaseApk -Algorithm SHA256
"$($hash.Hash.ToLowerInvariant())  $([IO.Path]::GetFileName($releaseApk))" |
    Set-Content -LiteralPath (Join-Path $releaseDirectory 'SHA256SUMS.txt') -Encoding utf8

Write-Output "Release: $releaseApk"
