# Launch the client with canonical Gradle cache paths to avoid duplicate modules.
$ErrorActionPreference = 'Stop'
$taskCache = $env:GRADLE_USER_HOME
if (-not $taskCache) { $taskCache = Join-Path $env:USERPROFILE '.gradle' }
$taskCacheItem = Get-Item -LiteralPath $taskCache
if ($taskCacheItem.Target) { $taskCache = @($taskCacheItem.Target)[0] }
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    & .\gradlew.bat --gradle-user-home $taskCache runClient --no-configuration-cache
    if ($LASTEXITCODE -ne 0) { throw "Client launch exited with code $LASTEXITCODE" }
} finally {
    Pop-Location
}
