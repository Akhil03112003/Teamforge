# Dot-source in a fresh benchmark terminal. Never reads or modifies root .env.
$ErrorActionPreference = 'Stop'
$benchmarkRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$benchmarkFile = Join-Path $benchmarkRoot '.env.benchmark'
if (-not (Test-Path -LiteralPath $benchmarkFile)) { throw 'Copy .env.benchmark.example to .env.benchmark and fill private placeholders.' }
$benchmarkSettings = @{}
foreach ($line in Get-Content -LiteralPath $benchmarkFile) {
    if ($line -match '^\s*(#|$)') { continue }
    $parts = $line -split '=', 2
    if ($parts.Count -ne 2 -or $benchmarkSettings.ContainsKey($parts[0].Trim())) { throw 'Invalid/duplicate benchmark setting; values withheld.' }
    $benchmarkSettings[$parts[0].Trim()] = $parts[1].Trim()
}
foreach ($name in @('PGHOST','PGPORT','PGUSER','PGPASSWORD','PGDATABASE','BENCHMARK_PASSWORD','BASE_URL')) {
    if (-not $benchmarkSettings[$name] -or $benchmarkSettings[$name].StartsWith('replace_')) { throw "Set $name privately in .env.benchmark." }
}
if ($benchmarkSettings.PGDATABASE -notmatch '^[a-z0-9_]+$') { throw 'Invalid database name.' }
if ($benchmarkSettings.PGSCHEMA) {
    if ($benchmarkSettings.PGSCHEMA -notmatch '^teamforge_benchmark_[a-z0-9_]+$') { throw 'Only an isolated teamforge_benchmark_* schema is allowed.' }
} elseif ($benchmarkSettings.PGDATABASE -notmatch '^teamforge_benchmark_[a-z0-9_]+$') { throw 'Require a dedicated benchmark database or isolated PGSCHEMA.' }
if ($benchmarkSettings.PGHOST -notmatch '^[a-zA-Z0-9.-]+$' -or $benchmarkSettings.PGPORT -notmatch '^\d{1,5}$' -or [int]$benchmarkSettings.PGPORT -notin 1..65535) { throw 'Invalid database host/port.' }
foreach ($name in @('PGHOST','PGPORT','PGUSER','PGPASSWORD','PGDATABASE','BENCHMARK_PASSWORD','BASE_URL')) {
    [Environment]::SetEnvironmentVariable($name, $benchmarkSettings[$name], 'Process')
}
if ($benchmarkSettings.BENCHMARK_PASSWORD_HASH) { $env:BENCHMARK_PASSWORD_HASH = $benchmarkSettings.BENCHMARK_PASSWORD_HASH }
$env:DB_URL = "jdbc:postgresql://$($env:PGHOST):$($env:PGPORT)/$($env:PGDATABASE)"
$env:PGSCHEMA = $benchmarkSettings.PGSCHEMA
if ($env:PGSCHEMA) {
    $env:DB_URL += "?currentSchema=$($env:PGSCHEMA)"
    $env:PGOPTIONS = "-c search_path=$($env:PGSCHEMA)"
    $env:SPRING_FLYWAY_DEFAULT_SCHEMA = $env:PGSCHEMA
    $env:SPRING_FLYWAY_SCHEMAS = $env:PGSCHEMA
} else {
    $env:PGOPTIONS = '-c search_path=public'
    $env:SPRING_FLYWAY_DEFAULT_SCHEMA = 'public'
    $env:SPRING_FLYWAY_SCHEMAS = 'public'
}
$env:DB_USERNAME = $env:PGUSER
$env:DB_PASSWORD = $env:PGPASSWORD
$env:DB_POOL_MAX = '10'
$env:SPRING_PROFILES_ACTIVE = 'local'
# Do not inherit a different migration target from another environment.
$env:FLYWAY_DB_URL = $env:DB_URL
$env:FLYWAY_DB_USERNAME = $env:DB_USERNAME
$env:FLYWAY_DB_PASSWORD = $env:DB_PASSWORD
