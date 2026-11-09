# Dot-source from PowerShell: . ./scripts/Set-DevelopmentEnvironment.ps1
$ErrorActionPreference = 'Stop'
$envFile = Join-Path (Split-Path $PSScriptRoot -Parent) '.env'
if (-not (Test-Path -LiteralPath $envFile)) {
    throw 'Copy .env.example to .env and replace the placeholders first.'
}
$settings = @{}
foreach ($line in Get-Content -LiteralPath $envFile) {
    if ($line -match '^\s*(#|$)') { continue }
    $parts = $line -split '=', 2
    if ($parts.Count -ne 2) { throw 'Invalid .env entry. Use KEY=value without shell quotes.' }
    $key = $parts[0].Trim()
    if ($key -notmatch '^[A-Z][A-Z0-9_]*$' -or $settings.ContainsKey($key)) {
        throw 'Invalid or duplicate .env key. Values were not printed.'
    }
    $settings[$key] = $parts[1].Trim()
}
foreach ($key in @('POSTGRES_DB', 'POSTGRES_USER', 'POSTGRES_PASSWORD', 'POSTGRES_PORT')) {
    if (-not $settings[$key] -or $settings[$key] -like 'replace_*') {
        throw "Set $key in .env before starting the backend."
    }
}
$databasePort = 0
if (-not [int]::TryParse($settings['POSTGRES_PORT'], [ref]$databasePort) -or
    $databasePort -lt 1 -or $databasePort -gt 65535) {
    throw 'POSTGRES_PORT must be an integer between 1 and 65535.'
}
$databaseName = [Uri]::EscapeDataString($settings['POSTGRES_DB'])
$env:DB_URL = "jdbc:postgresql://localhost:$databasePort/$databaseName"
$env:DB_USERNAME = $settings['POSTGRES_USER']
$env:DB_PASSWORD = $settings['POSTGRES_PASSWORD']
