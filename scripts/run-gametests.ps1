# Launch local GameTests offline with canonical Gradle cache paths to avoid duplicate modules.
$ErrorActionPreference = 'Stop'
$sporeCache = $env:GRADLE_USER_HOME
if (-not $sporeCache) { $sporeCache = Join-Path $env:USERPROFILE '.gradle' }
$sporeCacheItem = Get-Item -LiteralPath $sporeCache
if ($sporeCacheItem.Target) { $sporeCache = @($sporeCacheItem.Target)[0] }
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    & .\gradlew.bat --gradle-user-home $sporeCache runGameTestServer --offline --no-configuration-cache
    if ($LASTEXITCODE -ne 0) { throw "GameTests exited with code $LASTEXITCODE" }
} finally {
    Pop-Location
}
