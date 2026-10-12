# Design

## Context

See proposal.md for motivation and the two delta specs for observable behavior. The repository currently has only an IntelliJ template `src/Main.java`, no build configuration, and no existing compiler code. Stable 0.4 is specified in `openspec/context/specification_language/Спецификация Stable 0.4.md`. Its lexical rules, expression types, scopes, function ordering, and runtime rules must be enforced before a Java file is written. The local JDK is 25; generated source will use ordinary Java constructs and standard APIs compatible with Java 17 or later.

## Goals / Non-Goals

**Goals:**
- Keep source recognition, semantic checks, and Java emission separable and testable.
- Preserve Stable semantics even where direct text substitution to Java would fail.
- Keep the generated file self-contained and compilable without additional dependencies.

**Non-Goals:**
- One-pass translation, optimization, bytecode generation, or interpreting Stable directly.
- Automatically invoking `javac` as part of normal translation; compilation is an acceptance check and a separate user action.

## Decisions

### 1. Four sequential phases with an AST

Use one lexer, one recursive-descent parser, one semantic analyzer, and one Java emitter. The parser builds an abstract syntax tree carrying source spans. Expression parsing uses precedence levels; type validity is checked against Stable's type rules in the semantic phase. The analyzer annotates or maps expressions and declarations to resolved types and symbols, and the emitter consumes only successfully checked trees.

Alternative: directly emit Java during parsing, as in the lecture's one-pass example. That couples name resolution and code generation to parsing and makes nested shadowing, function flow checks, and failed-output guarantees harder to maintain. A generated parser framework is unnecessary for this compact grammar.

### 2. Scope and type model

Represent `hoof`, `stride`, `neigh`, `tamed`, and `herd<T>` as explicit types. Keep a stack of variable scopes and a separate ordered function table. Add a function signature before checking its body, allowing self-recursion; expose earlier functions to later bodies, never later functions to earlier bodies. Treat parameters as declarations in the function body's scope. Track a read-only flag for the generated `for` variable. Check all expressions, including those in unreachable statements, while recording whether each statement sequence can fall through for return analysis.

Alternative: use Java's type checker as the Stable checker. It would accept prohibited conversions, report Java names and locations, and miss Stable-specific rules.

### 3. Generated Java names and file layout

The CLI takes one source path and writes `Zherebtsov<SourceName>.java` in the same directory, reporting that path. Derive `SourceName` from the final source filename without its final extension: uppercase its first ASCII letter and the first letter after an encoded separator, preserve subsequent ASCII letters and digits, and encode every other UTF-16 code unit as `_uXXXX_`. Declare the matching class `public final` with `public static void main(String[] args)`. Assign collision-free Java names to every Stable declaration, function, helper, and temporary. All Stable references use their resolved symbols rather than original spelling. This also permits nested Stable shadowing and identifiers reserved by Java.

Alternative: preserve Stable names verbatim or choose a Java class name independently of the output filename. Verbatim names can make valid Stable fail `javac`; unrelated class and file names make the generated program harder to run.

### 4. Stable-specific Java lowering

Map simple types to `int`, `double`, `String`, and `boolean`, and `herd<T>` to Java arrays. Generate content comparison for `neigh` equality, preserve array reference sharing, and use Java's left-to-right evaluation and short-circuit operators where equivalent. Emit array literals using the expected array type. Lower inclusive `for` into start/end temporaries evaluated in that order and a loop that stops after the final iteration even when the end is `Integer.MAX_VALUE`. Use helper methods for typed input, `tame`/`wild` output, and `stride` division by zero. Input consumes one line per `input<T>()` call; `neigh` uses the complete line, while other types parse and validate that line. This follows the line-based input examples in Stable 0.4.

Avoid Java compile-time reachability conflicts by omitting statements that are unreachable after an unconditional return during emission, after validating them, and by emitting runtime boolean expressions for Stable constant loop conditions when needed. Handle `-2147483648` as one permitted signed `hoof` value rather than emitting an out-of-range positive literal. Check literal representability before emission so `javac` does not become the first source-level error reporter.

Alternative: render source by replacing keywords and operators. That would break string equality, boolean I/O, `for` boundaries, Java name rules, and reachability.

### 5. Error and output boundary

Return structured diagnostics with category, source line, column, and message. Parse and analyze fully before rendering. Render the complete Java text in memory, then replace the target file only after a successful translation; source-language errors leave any existing target untouched. Report I/O failures separately. Use no external parser or runtime library.

Alternative: stream generated code to the target while parsing. A late error could leave a partial or replaced `.java` file.

## Risks / Trade-offs

- [The typed Stable expression grammar shares prefixes between numeric, string, and boolean expressions] -> Use one precedence parser and enforce each operator/type combination semantically; invalid programs still fail before output.
- [Java treats constant control flow and unreachable statements differently] -> Check Stable return paths independently and use lowering that avoids Java-only reachability failures.
- [Filename stems and Stable identifiers can contain characters unsuitable for Java names] -> Encode unsuitable filename characters, use a fixed class prefix, and resolve every Stable symbol to a unique Java name.
- [The Stable document does not define every input whitespace detail] -> Use one line per call, retain the full line for `neigh`, and cover the convention in executable examples.
- [The generated Java source can be large] -> Start with whole-file AST and rendering; optimization is out of scope for Stable 0.4.

## Migration Plan

Replace the template entry point with the translator CLI. There is no existing translator data or API to migrate. Verify compilation of the translator and representative generated files with `javac`, then execute behavior and invalid-source fixtures. Reverting the new code restores the previous template state.
