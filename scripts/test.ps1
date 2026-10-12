param([switch]$SkipFixtures)

$ErrorActionPreference = 'Stop'
$project = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
& (Join-Path $PSScriptRoot 'build.ps1') -WithTests
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Push-Location $project
try {
    & java -cp (Join-Path $project 'out/test-classes') stable.compiler.tests.TranslatorTests
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    if (-not $SkipFixtures) {
        & (Join-Path $PSScriptRoot 'test-fixtures.ps1') -SkipBuild
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
} finally {
    Pop-Location
}
