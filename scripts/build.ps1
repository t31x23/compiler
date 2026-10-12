# Если указан WithTests, дополнительно компилируем тесты вместе с кодом транслятора.
param([switch]$WithTests)

$ErrorActionPreference = 'Stop'
# Пути строятся от расположения скрипта, поэтому запуск возможен из любого каталога.
$project = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$sourceDir = Join-Path $project 'src/stable/compiler'
$testDir = Join-Path $sourceDir 'tests'
$classes = Join-Path $project 'out/classes'
# Рекурсивно собираем рабочие пакеты, исключая каталог тестов.
$sources = @(Get-ChildItem -LiteralPath $sourceDir -Recurse -File -Filter '*.java' |
    Where-Object { $_.FullName -notlike "$testDir\*" } | ForEach-Object FullName)
if ($sources.Count -eq 0) { throw "No compiler sources found in $sourceDir" }
# javac сохраняет классы в структуре каталогов, соответствующей именам пакетов.
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& javac -encoding UTF-8 -d $classes $sources
# Ненулевой код javac должен остановить сборку, а не скрыться за следующим шагом.
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if ($WithTests) {
    # Отдельный выходной каталог не смешивает тестовые классы с обычной сборкой.
    $testClasses = Join-Path $project 'out/test-classes'
    $tests = @(Get-ChildItem -LiteralPath $testDir -File -Filter '*.java' | ForEach-Object FullName)
    if ($tests.Count -eq 0) { throw "No test sources found in $testDir" }
    New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
    # Одним вызовом javac собираем тесты и классы, от которых они зависят.
    & javac -encoding UTF-8 -d $testClasses ($sources + $tests)
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
