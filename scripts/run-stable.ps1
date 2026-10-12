


param([Parameter(Mandatory=$true, Position=0)][string]$Source)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$compilerOut = Join-Path $root 'out/classes'
$programOut = Join-Path $root 'out/generated-classes'

if ([IO.Path]::IsPathRooted($Source)) {
    $candidate = $Source
} elseif (Test-Path -LiteralPath (Join-Path $root $Source) -PathType Leaf) {
    $candidate = Join-Path $root $Source
} else {
    $candidate = Join-Path (Get-Location).Path $Source
}
if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) { throw "Stable file not found: $Source" }
$sourceFile = (Resolve-Path -LiteralPath $candidate).Path
if ([IO.Path]::GetExtension($sourceFile) -ne '.stable') { throw 'Expected .stable file' }

function Check-Exit([string]$step) {
    if ($LASTEXITCODE -ne 0) { throw "$step failed (exit code $LASTEXITCODE)" }
}
New-Item -ItemType Directory -Force -Path $programOut | Out-Null

Write-Host '[1/4] Building Stable compiler...'
& (Join-Path $PSScriptRoot 'build.ps1')
Check-Exit 'Compiler build'

Write-Host '[2/4] Translating Stable to Java...'
$javaFile = (& java -cp $compilerOut stable.compiler.Main $sourceFile | Select-Object -Last 1)
Check-Exit 'Translation'
if (-not (Test-Path -LiteralPath $javaFile -PathType Leaf)) { throw "Java file not generated: $javaFile" }
$className = [IO.Path]::GetFileNameWithoutExtension($javaFile)

Write-Host '[3/4] Compiling generated Java...'
& javac -encoding UTF-8 -d $programOut $javaFile
Check-Exit 'Java compilation'

Write-Host '[4/4] Running...'
Write-Host '--------------------'
& java -cp $programOut $className
Check-Exit 'Program execution'

