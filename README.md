# Stable 0.4 → Java

Транслятор проверяет исходный код Stable 0.4 и создаёт самостоятельный Java-файл. Требуется JDK 17 или новее. Синтаксис и правила языка описаны в `openspec/context/specification_language/Спецификация Stable 0.4.md`.

Из корня проекта в PowerShell:

```powershell
.\scripts\build.ps1
java -cp out/classes stable.compiler.Main examples\hello.stable
New-Item -ItemType Directory -Force out/generated | Out-Null
javac -d out/generated examples\ZherebtsovHello.java
java -cp out/generated ZherebtsovHello
```

Последняя команда ждёт одну строку ввода. Например, после ввода `Storm` программа напечатает `Hello, Storm`.

Команда транслятора принимает ровно один путь к исходному файлу. Она создаёт рядом файл `Zherebtsov<Имя>.java` и печатает его путь. `<Имя>` получается из имени исходного файла без расширения: первая буква каждого фрагмента становится заглавной, остальные буквы и цифры сохраняются. Небуквенные символы кодируются как `_uXXXX_` (шестнадцатеричный код символа). Например, `hello.stable` превращается в `ZherebtsovHello.java`. Внутри находится одноимённый публичный класс.

Каждый вызов `input<T>()` читает **одну строку** стандартного ввода. `input<neigh>()` возвращает её целиком; `input<hoof>()`, `input<stride>()` и `input<tamed>()` проверяют формат числа или значения `tame`/`wild`. `print` выводит значение и перевод строки.

При лексической, синтаксической или семантической ошибке транслятор показывает категорию, строку и столбец, завершается с ошибкой и не заменяет существующий выходной файл. Транслятор сам не вызывает `javac`.

Проверка Java-тестов и всех примеров Stable из `examples/stable_tests`:

```powershell
.\scripts\test.ps1
```

Для запуска только Java-тестов используйте `.\scripts\test.ps1 -SkipFixtures`; для проверки только примеров — `.\scripts\test-fixtures.ps1`. Чтобы собрать, транслировать и выполнить один `.stable`-файл, запустите `.\scripts\run-stable.ps1 examples\hello.stable`.

Исходный код транслятора находится в `src/stable/compiler`: `Main` обрабатывает командную строку, `Translator` запускает лексер, парсер, семантический анализ и генератор Java. Тесты находятся в отдельном пакете `src/stable/compiler/tests`. Скрипт сборки кладёт классы транслятора в `out/classes`, а тестовую сборку — в `out/test-classes`.
