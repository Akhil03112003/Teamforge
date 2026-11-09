[CmdletBinding()]
param(
    [ValidateSet('run', 'compile', 'verify')]
    [string]$Mode = 'run',
    [string]$JavaHome = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'Set-DevelopmentEnvironment.ps1')

if ($JavaHome) {
    if (-not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/javac.exe'))) {
        throw 'JavaHome must point to an installed JDK 21 directory.'
    }
    $env:JAVA_HOME = $JavaHome
    $env:PATH = (Join-Path $JavaHome 'bin') + [IO.Path]::PathSeparator + $env:PATH
}
$javaVersion = (& java --version | Out-String)
if ($LASTEXITCODE -ne 0 -or $javaVersion -notmatch '(?m)^(openjdk|java) 21(\.|\s)') {
    throw 'JDK 21 is required for this workflow. Pass -JavaHome with its installation directory.'
}
Get-Command javac.exe, mvn.cmd -ErrorAction Stop | Out-Null
$goal = switch ($Mode) {
    'run' { 'spring-boot:run' }
    'compile' { 'clean'; 'compile' }
    'verify' { 'verify' }
}
$previousDatabaseTests = $env:TEAMFORGE_DATABASE_TESTS
try {
    if ($Mode -eq 'verify') { $env:TEAMFORGE_DATABASE_TESTS = 'true' }
    & mvn.cmd --batch-mode --no-transfer-progress -f (Join-Path $repositoryRoot 'backend/pom.xml') @goal
    if ($LASTEXITCODE -ne 0) { throw 'Maven failed. See its output; no success is claimed.' }
} finally {
    $env:TEAMFORGE_DATABASE_TESTS = $previousDatabaseTests
}
