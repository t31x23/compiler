


# Первый аргумент — обязательный путь к программе Stable.
param([Parameter(Mandatory=$true, Position=0)][string]$Source)
$ErrorActionPreference = 'Stop'
# Каталоги сборки определяем относительно скрипта, а не текущего каталога.
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$compilerOut = Join-Path $root 'out/classes'
$programOut = Join-Path $root 'out/generated-classes'

# Принимаем абсолютный путь, путь от корня проекта или путь от текущего каталога.
if ([IO.Path]::IsPathRooted($Source)) {
    $candidate = $Source
} elseif (Test-Path -LiteralPath (Join-Path $root $Source) -PathType Leaf) {
    $candidate = Join-Path $root $Source
} else {
    $candidate = Join-Path (Get-Location).Path $Source
}
if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) { throw "Stable file not found: $Source" }
$sourceFile = (Resolve-Path -LiteralPath $candidate).Path
# Переводим только файлы языка Stable.
if ([IO.Path]::GetExtension($sourceFile) -ne '.stable') { throw 'Expected .stable file' }

function Check-Exit([string]$step) {
    # Внешние команды сообщают о неудаче кодом процесса; превращаем его в ошибку скрипта.
    if ($LASTEXITCODE -ne 0) { throw "$step failed (exit code $LASTEXITCODE)" }
}
# Классы готовой программы храним отдельно от самого транслятора.
New-Item -ItemType Directory -Force -Path $programOut | Out-Null

Write-Host '[1/4] Building Stable compiler...'
# Общая сборка используется и для тестов, и для ручного запуска.
& (Join-Path $PSScriptRoot 'build.ps1')
Check-Exit 'Compiler build'

Write-Host '[2/4] Translating Stable to Java...'
# CLI печатает путь результата; из него берём точное имя Java-класса.
$javaFile = (& java -cp $compilerOut stable.compiler.Main $sourceFile | Select-Object -Last 1)
Check-Exit 'Translation'
if (-not (Test-Path -LiteralPath $javaFile -PathType Leaf)) { throw "Java file not generated: $javaFile" }
$className = [IO.Path]::GetFileNameWithoutExtension($javaFile)

Write-Host '[3/4] Compiling generated Java...'
# javac получает сгенерированный файл, а классы кладёт в out/.
& javac -encoding UTF-8 -d $programOut $javaFile
Check-Exit 'Java compilation'

Write-Host '[4/4] Running...'
Write-Host '--------------------'
# Программа использует стандартный ввод и вывод текущей консоли.
& java -cp $programOut $className
Check-Exit 'Program execution'

