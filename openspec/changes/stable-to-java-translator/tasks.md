# Tasks

## 1. Source front end

- [x] 1.1 Replace the template entry point with a translator package layout and define tokens, source spans, diagnostics, and AST node types; verify `javac` compiles the source tree.
- [x] 1.2 Implement the Stable 0.4 lexer for keywords, identifiers, numeric and string literals, comments, operators, longest-match tokenization, and UTF-8 locations; verify focused lexer fixtures including `//` inside strings and invalid characters.
- [x] 1.3 Implement one recursive-descent parser for program structure, types, functions, statements, arrays, and precedence-aware expressions; verify parser fixtures for valid programs, precedence, missing delimiters, and trailing tokens.

## 2. Stable semantic analysis

- [x] 2.1 Implement Stable types, expression type checking, array-literal context, indexes, assignment rules, and operator restrictions; verify valid array reference assignment and rejected mixed-type fixtures.
- [x] 2.2 Implement nested variable scopes, separate function and variable namespaces, declaration order, parameter scopes, and read-only `for` variables; verify shadowing, recursion, unknown names, duplicate names, and forbidden loop-variable assignments.
- [x] 2.3 Implement function call checks, return-type checks, and all-path return analysis while validating unreachable statements; verify missing-return, incorrect-call, and code-after-return fixtures.
- [x] 2.4 Enforce literal ranges and source error categories with line/column diagnostics; verify `-2147483648` is accepted and out-of-range literals and invalid source fail before any Java file is written.

## 3. Java emission and runtime behavior

- [x] 3.1 Implement deterministic Java naming, matching public class and filename emission, `main`, functions, declarations, assignments, and arrays; verify generated files with Java keywords, unusual source stems, and nested Stable shadowing compile via `javac`.
- [x] 3.2 Emit typed expressions with preserved precedence, left-to-right evaluation, short circuiting, string content equality, and array reference sharing; verify compiled Java outputs against Stable examples and side-effect fixtures.
- [x] 3.3 Emit `if`, `while`, `for`, function returns, and Java-safe unreachable-code handling; verify nested control flow, once-only inclusive bounds, `Integer.MAX_VALUE`, and post-return fixtures by compiling and running output.
- [x] 3.4 Emit self-contained input/output and arithmetic helpers; verify all four input types, `tame`/`wild` output, invalid typed input, and division-by-zero behavior with compiled programs.

## 4. File command and integration

- [x] 4.1 Implement the one-path CLI, UTF-8 file handling, `Zherebtsov<SourceName>.java` naming, useful I/O errors, and write-on-success replacement; verify successful path reporting and that invalid source leaves a pre-existing target untouched.
- [x] 4.2 Document how to compile and run the translator and generated Java, including the matching public class name and one-line-per-input-call convention; verify every documented command on a sample program.
- [x] 4.3 Run an end-to-end fixture set spanning functions, arrays, scopes, arithmetic, control flow, input/output, lexical/syntax/semantic errors, and runtime errors; verify every valid generated file compiles with `javac` and each fixture has the expected result.
