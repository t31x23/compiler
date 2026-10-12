# Позволяет быстро запустить только Java-тесты без примеров из stable_tests.
param([switch]$SkipFixtures)

$ErrorActionPreference = 'Stop'
$project = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
# Перед запуском тестов собираем их и сам транслятор.
& (Join-Path $PSScriptRoot 'build.ps1') -WithTests
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Push-Location $project
try {
    # Тесты создают временные файлы внутри out/, поэтому запускаются из корня проекта.
    & java -cp (Join-Path $project 'out/test-classes') stable.compiler.tests.TranslatorTests
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    if (-not $SkipFixtures) {
        # Полный прогон проверяет также готовые программы Stable; повторная сборка не нужна.
        & (Join-Path $PSScriptRoot 'test-fixtures.ps1') -SkipBuild
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
} finally {
    # После тестов восстанавливаем прежний рабочий каталог вызывающего процесса.
    Pop-Location
}
