# Spec Delta

## Purpose

Produces standalone Java source files that compile and execute with the observable behavior defined for valid Stable 0.4 programs.

## ADDED Requirements

### Requirement: File-based translation
The translator SHALL accept a readable Stable source-file path, decode it as UTF-8, and write a Java file named `Zherebtsov<SourceName>.java` beside it after successful validation. `SourceName` is derived deterministically from the source filename stem and is a valid Java identifier suffix.

#### Scenario: Named source file
- **WHEN** the user translates `examples/horse.stable`
- **THEN** the translator writes `examples/ZherebtsovHorse.java` and reports that path

#### Scenario: Unreadable input
- **WHEN** the source path is missing or unreadable
- **THEN** the command exits unsuccessfully with a useful file error and creates no Java output

### Requirement: Compilable standalone Java
The translator SHALL generate UTF-8 Java source that compiles with `javac` and has an executable `main` method corresponding to Stable `run`, using only standard Java APIs.

#### Scenario: Matching public class and filename
- **WHEN** a valid Stable file is translated
- **THEN** the generated Java declares a public class whose name matches the `.java` filename and `javac` accepts it

#### Scenario: Non-identifier source filename
- **WHEN** the source filename stem contains a character that cannot be used literally in the class name
- **THEN** the translator encodes that character deterministically and writes a Java file with a valid matching public class name

#### Scenario: Stable names conflicting with Java
- **WHEN** a valid Stable program uses an identifier that is a Java keyword, a generated helper name, or a name shadowed in a nested block
- **THEN** the generated Java still compiles and preserves name resolution

### Requirement: Primitive and string behavior
Generated Java SHALL preserve Stable `hoof`, `stride`, `neigh`, and `tamed` values, permitted operations, expression precedence, and left-to-right evaluation.

#### Scenario: Integer arithmetic and overflow
- **WHEN** a Stable program calculates with `hoof`, including overflow or integer division
- **THEN** the generated program has Stable's signed 32-bit arithmetic and division-toward-zero behavior

#### Scenario: String content equality
- **WHEN** a Stable program compares two `neigh` values using `==` or `!=`
- **THEN** the generated program compares their character content

#### Scenario: Logical short circuit
- **WHEN** the left side determines the result of `&&` or `||`
- **THEN** the right side is not evaluated

### Requirement: Arrays and functions
Generated Java SHALL preserve zero-based arrays, reference sharing on assignment and parameter passing, function calls, recursion, argument order, and return values.

#### Scenario: Shared array
- **WHEN** a Stable program assigns an array to another variable and changes an element through the second variable
- **THEN** reading that element through the first variable returns the changed value

#### Scenario: Function arguments
- **WHEN** a function call has multiple argument expressions with observable effects
- **THEN** they are evaluated exactly once from left to right

#### Scenario: Out-of-bounds access
- **WHEN** a program accesses an invalid array index
- **THEN** execution fails at runtime

### Requirement: Control-flow behavior
Generated Java SHALL preserve Stable `if`, `while`, and `for` behavior, including nested blocks and returns.

#### Scenario: Inclusive for boundary
- **WHEN** a program executes `for i from 1 to 3`
- **THEN** its body observes `1`, `2`, and `3` in that order

#### Scenario: Bounds evaluated once
- **WHEN** the start and end expressions of `for` have observable effects
- **THEN** each is evaluated exactly once, start before end, before the first iteration

#### Scenario: Maximum integer boundary
- **WHEN** a `for` loop reaches the maximum `hoof` value as its inclusive end
- **THEN** it executes that final iteration and terminates without wrapping to the minimum value

### Requirement: Stable input and output
Generated Java SHALL implement `input<T>()` and `print` for simple types as specified by Stable 0.4, including Stable boolean spelling and invalid-input failures.

#### Scenario: Boolean round trip
- **WHEN** a program reads `tame` with `input<tamed>()` and prints the result
- **THEN** it prints `tame`

#### Scenario: Invalid typed input
- **WHEN** `input<hoof>()` receives `42.5`, `input<stride>()` receives a value without a decimal point, or `input<tamed>()` receives another word
- **THEN** execution fails at runtime

### Requirement: Runtime arithmetic errors
Generated Java SHALL fail at runtime for integer division or remainder by zero and for `stride` division by zero.

#### Scenario: Real division by zero
- **WHEN** a Stable program divides a `stride` value by `0.0`
- **THEN** execution fails at runtime instead of producing infinity or NaN
