# Proposal

## Why

The repository contains a Stable 0.4 language specification but no translator. A translator is needed to turn valid Stable programs into Java source that compiles and preserves Stable behavior, while reporting invalid Stable programs before emitting output.

## What Changes

- Add a file-based Stable-to-Java translator with lexical, syntax, and semantic checks covering Stable 0.4.
- Represent parsed programs as an abstract syntax tree (AST), then generate Java only after validation succeeds.
- Write output as `Zherebtsov<SourceName>.java` with a matching public Java class and provide a command-line entry point for translation.
- Add executable examples and checks that compile generated Java and exercise its behavior and error reporting.

## Capabilities

### New Capabilities

- `stable-source-validation`: Recognize Stable 0.4 programs and reject lexical, syntax, and semantic errors with source locations.
- `java-source-generation`: Emit compilable Java source with Stable-compatible runtime behavior and the agreed output-file naming convention.

### Modified Capabilities

None.

## Impact

Replaces the template `src/Main.java` with a Java translator and adds parser, AST, semantic-analysis, code-generation, and test code. Generated programs use only standard Java APIs; the local JDK is available for compilation checks. The Stable 0.4 document remains the language authority.
