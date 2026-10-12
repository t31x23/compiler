param([switch]$WithTests)

$ErrorActionPreference = 'Stop'
$project = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$sourceDir = Join-Path $project 'src/stable/compiler'
$classes = Join-Path $project 'out/classes'
$sources = @(Get-ChildItem -LiteralPath $sourceDir -File -Filter '*.java' | ForEach-Object FullName)
if ($sources.Count -eq 0) { throw "No compiler sources found in $sourceDir" }
New-Item -ItemType Directoryиг -Force -Path $classes | Out-Null
& javac -encoding UTF-8 -d $classes $sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if ($WithTests) {
    $testDir = Join-Path $sourceDir 'tests'
    $testClasses = Join-Path $project 'out/test-classes'
    $tests = @(Get-ChildItem -LiteralPath $testDir -File -Filter '*.java' | ForEach-Object FullName)
    if ($tests.Count -eq 0) { throw "No test sources found in $testDir" }
    New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
    & javac -encoding UTF-8 -d $testClasses ($sources + $tests)
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
