# SkipBuild используют после test.ps1, когда транслятор уже собран.
param([switch]$SkipBuild, [string]$Suite)

$ErrorActionPreference = 'Stop'
# Находим набор примеров и классы транслятора независимо от текущего каталога.
$project = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$suite = if ($Suite) { (Resolve-Path -LiteralPath $Suite).Path } else { Join-Path $project 'examples/stable_tests' }
$classes = Join-Path $project 'out/classes'
if (-not $SkipBuild) {
    # При самостоятельном запуске сначала собираем рабочий код.
    & (Join-Path $PSScriptRoot 'build.ps1')
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

# Каждый прогон работает с копиями файлов в уникальном каталоге внутри out/.
$runDir = Join-Path $project ('out/fixture-runs/' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
$passed = 0
$failed = 0

function Invoke-Tool([string]$program, [string[]]$arguments, [string]$stdinText = '') {
    # Запускаем java/javac как отдельный процесс и собираем код выхода и оба потока.
    # Ввод отправляем как UTF-8, чтобы проверить программы с input<T>().
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $program
    $info.WorkingDirectory = $project
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardInput = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.StandardInputEncoding = [Text.UTF8Encoding]::new($false)
    $info.StandardOutputEncoding = [Text.Encoding]::UTF8
    $info.StandardErrorEncoding = [Text.Encoding]::UTF8
    foreach ($argument in $arguments) { [void]$info.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::Start($info)
    try {
        # Закрытый stdin сообщает программе о конце входных данных.
        $process.StandardInput.Write($stdinText)
        $process.StandardInput.Close()
        $stdout = $process.StandardOutput.ReadToEnd()
        $stderr = $process.StandardError.ReadToEnd()
        $process.WaitForExit()
        return [pscustomobject]@{ Code = $process.ExitCode; Out = $stdout; Err = $stderr }
    } finally {
        # Освобождаем системные ресурсы процесса и при успешном, и при ошибочном запуске.
        $process.Dispose()
    }
}

function Get-ClassName([string]$stem) {
    # Повторяем правило имени генератора, чтобы заранее знать ожидаемый Java-файл.
    $name = [Text.StringBuilder]::new('Zherebtsov')
    $capitalize = $true
    foreach ($ch in $stem.ToCharArray()) {
        if ($ch -cmatch '[A-Za-z0-9]') {
            # Первый допустимый символ каждого фрагмента делаем заглавным.
            if ($capitalize) { [void]$name.Append([char]::ToUpperInvariant($ch)) }
            else { [void]$name.Append($ch) }
            $capitalize = $false
        } else {
            # Небуквенные символы кодируются как _uXXXX_.
            [void]$name.Append('_u').Append(([int]$ch).ToString('X4')).Append('_')
            $capitalize = $true
        }
    }
    return $name.ToString()
}

function Add-Result([string]$name, [bool]$success, [string]$reason) {
    # Печатаем понятный статус каждого случая и ведём итоговые счётчики.
    if ($success) {
        $script:passed++
        Write-Host "PASS $name"
    } else {
        $script:failed++
        Write-Host "FAIL $name : $reason"
    }
}

function Prepare-Case([IO.FileInfo]$source, [string]$kind) {
    # Копия позволяет проверять выходной файл, не меняя исходные тестовые программы.
    $directory = Join-Path $runDir "$kind-$($source.BaseName)"
    New-Item -ItemType Directory -Path $directory | Out-Null
    $copy = Join-Path $directory $source.Name
    Copy-Item -LiteralPath $source.FullName -Destination $copy
    $className = Get-ClassName $source.BaseName
    return [pscustomobject]@{ Directory = $directory; Source = $copy; ClassName = $className; Java = (Join-Path $directory "$className.java") }
}

# Корректный случай должен пройти трансляцию, javac и запуск с ожидаемым выводом.
foreach ($source in @(Get-ChildItem -LiteralPath (Join-Path $suite 'valid') -Filter '*.stable' | Sort-Object Name)) {
    $case = Prepare-Case $source 'valid'
    $translation = Invoke-Tool 'java' @('-cp', $classes, 'stable.compiler.Main', $case.Source)
    if ($translation.Code -ne 0 -or -not (Test-Path -LiteralPath $case.Java)) {
        Add-Result $source.Name $false "translation: $($translation.Err)"
        continue
    }
    $compilation = Invoke-Tool 'javac' @('-encoding', 'UTF-8', '-d', $case.Directory, $case.Java)
    if ($compilation.Code -ne 0) {
        Add-Result $source.Name $false "javac: $($compilation.Err)"
        continue
    }
    # Вход берём из одноимённого файла; если его нет, программа получает пустой stdin.
    $inputFile = Join-Path $suite "input/$($source.BaseName).txt"
    $stdinText = if (Test-Path -LiteralPath $inputFile) { [IO.File]::ReadAllText($inputFile) } else { '' }
    $execution = Invoke-Tool 'java' @('-cp', $case.Directory, $case.ClassName) $stdinText
    $expectedFile = Join-Path $suite "expected/$($source.BaseName).txt"
    if (-not (Test-Path -LiteralPath $expectedFile -PathType Leaf)) {
        Add-Result $source.Name $false "нет файла ожидаемого вывода: $expectedFile"
        continue
    }
    # На Windows Java печатает CRLF, а эталоны могут храниться с LF.
    $expected = [IO.File]::ReadAllText($expectedFile).Replace("`r`n", "`n")
    $actual = $execution.Out.Replace("`r`n", "`n")
    Add-Result $source.Name ($execution.Code -eq 0 -and $actual -ceq $expected) "exit=$($execution.Code), actual=$($execution.Out), expected=$expected, stderr=$($execution.Err)"
}

# Для ошибочных исходников заранее задаём допустимые категории диагностики.
$categories = @{
    type_mismatch = @('Semantic'); string_multiply = @('Syntax', 'Semantic'); mixed_numbers = @('Syntax', 'Semantic')
    bad_condition = @('Semantic'); bad_index = @('Semantic'); for_assign = @('Semantic')
    unknown_variable = @('Semantic'); bad_return = @('Semantic'); syntax_missing_semicolon = @('Syntax')
    lexical_invalid_char = @('Lexical'); invalid_input_type = @('Syntax'); function_args = @('Semantic')
}
foreach ($source in @(Get-ChildItem -LiteralPath (Join-Path $suite 'invalid') -Filter '*.stable' | Where-Object BaseName -NotLike 'runtime_*' | Sort-Object Name)) {
    # Первый запуск проверяет, что неудача не создаёт Java-файл.
    $case = Prepare-Case $source 'invalid'
    $first = Invoke-Tool 'java' @('-cp', $classes, 'stable.compiler.Main', $case.Source)
    $created = Test-Path -LiteralPath $case.Java
    # Второй запуск проверяет, что существующий Java-файл не заменяется.
    $sentinel = 'EXISTING JAVA FILE MUST STAY UNCHANGED'
    [IO.File]::WriteAllText($case.Java, $sentinel)
    $second = Invoke-Tool 'java' @('-cp', $classes, 'stable.compiler.Main', $case.Source)
    $preserved = [IO.File]::ReadAllText($case.Java) -ceq $sentinel
    # Из stderr извлекаем категорию и проверяем её вместе с кодами завершения.
    $category = if ($first.Err -match '^(Lexical|Syntax|Semantic) error at \d+:\d+:') { $Matches[1] } else { 'missing' }
    $success = $first.Code -ne 0 -and $second.Code -ne 0 -and $categories[$source.BaseName] -contains $category -and -not $created -and $preserved
    Add-Result $source.Name $success "category=$category, exit=$($first.Code), created=$created, preserved=$preserved, stderr=$($first.Err)"
}

# Ошибки времени выполнения должны успешно пройти транслятор и javac.
foreach ($source in @(Get-ChildItem -LiteralPath (Join-Path $suite 'invalid') -Filter 'runtime_*.stable' | Sort-Object Name)) {
    $case = Prepare-Case $source 'runtime'
    $translation = Invoke-Tool 'java' @('-cp', $classes, 'stable.compiler.Main', $case.Source)
    if ($translation.Code -ne 0 -or -not (Test-Path -LiteralPath $case.Java)) {
        Add-Result $source.Name $false "translation: $($translation.Err)"
        continue
    }
    $compilation = Invoke-Tool 'javac' @('-encoding', 'UTF-8', '-d', $case.Directory, $case.Java)
    if ($compilation.Code -ne 0) {
        Add-Result $source.Name $false "javac: $($compilation.Err)"
        continue
    }
    $execution = Invoke-Tool 'java' @('-cp', $case.Directory, $case.ClassName)
    # Затем проверяем конкретное исключение и отсутствие обычного вывода.
    $expectedError = if ($source.BaseName -eq 'runtime_division_zero') { 'ArithmeticException' } else { 'ArrayIndexOutOfBoundsException' }
    Add-Result $source.Name ($execution.Code -ne 0 -and $execution.Err.Contains($expectedError) -and $execution.Out.Length -eq 0) "exit=$($execution.Code), stderr=$($execution.Err)"
}

Write-Host "Fixture tests: $passed passed, $failed failed. Files: $runDir"
# Провал хотя бы одного случая должен дать ненулевой код для CI и вызывающего скрипта.
if ($failed -gt 0) { exit 1 }
