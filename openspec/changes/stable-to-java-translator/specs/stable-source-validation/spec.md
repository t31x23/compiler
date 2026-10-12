# Spec Delta

## Purpose

Recognizes programs written in Stable 0.4 and reports source-level errors before any Java output is produced.

## ADDED Requirements

### Requirement: Stable lexical rules
The translator SHALL tokenize UTF-8 Stable 0.4 source according to its identifier, keyword, literal, operator, whitespace, comment, and longest-match rules.

#### Scenario: Comment outside a string
- **WHEN** source contains `//` outside a string literal
- **THEN** the translator ignores characters through the end of that line

#### Scenario: Comment marker inside a string
- **WHEN** source contains `"a//b"`
- **THEN** the entire content `a//b` is part of one string literal

#### Scenario: Invalid character
- **WHEN** source contains a character that cannot begin or continue a valid token
- **THEN** translation fails with a lexical error at that character

### Requirement: Stable program syntax
The translator SHALL accept exactly the program, declaration, statement, type, and expression forms defined by Stable 0.4 and SHALL reject malformed syntax or extra trailing tokens.

#### Scenario: Valid program structure
- **WHEN** source contains a `zherebtsov` program with zero or more functions before exactly one `run` block
- **THEN** syntax analysis succeeds

#### Scenario: Invalid program structure
- **WHEN** a function appears after `run` or a required delimiter is missing
- **THEN** translation fails with a syntax error

#### Scenario: Expression precedence
- **WHEN** source contains `hoof result = 2 + 3 * 4;` in `run`
- **THEN** the expression is understood as `2 + (3 * 4)`

### Requirement: Exact Stable types
The translator SHALL enforce the Stable 0.4 simple and array types, permitted operators, and exact type matching without implicit conversions.

#### Scenario: Mixed numeric types
- **WHEN** a `hoof` value and a `stride` value occur in one arithmetic operation or a value is assigned to a different type
- **THEN** translation fails with a semantic type error

#### Scenario: Array types and indexing
- **WHEN** an array literal contains a value of the wrong element type or an index is not `hoof`
- **THEN** translation fails with a semantic type error

#### Scenario: Invalid condition or output
- **WHEN** an `if` or `while` condition is not `tamed`, or `print` is passed a whole array
- **THEN** translation fails with a semantic type error

### Requirement: Names and block scopes
The translator SHALL enforce declaration-before-use, distinct declarations in each scope, nested shadowing, separate function and variable namespaces, and the lifetime of `for` variables.

#### Scenario: Nested shadowing
- **WHEN** an inner block declares a variable with the same name as an outer variable
- **THEN** references in the inner block resolve to the inner variable and references after the block resolve to the outer variable

#### Scenario: Duplicate or unknown variable
- **WHEN** a variable is declared twice in the same scope or used outside its visible scope
- **THEN** translation fails with a semantic name error

#### Scenario: Protected loop variable
- **WHEN** the body of a `for` loop explicitly assigns to its automatically created loop variable
- **THEN** translation fails with a semantic error

### Requirement: Function rules
The translator SHALL enforce unique function names, declaration-before-call with self-recursion allowed, exact argument counts and types, valid `return` placement and type, and a return on every possible function path.

#### Scenario: Earlier declaration and recursion
- **WHEN** a function calls itself or a previously declared function with matching arguments
- **THEN** semantic analysis accepts the call

#### Scenario: Later declaration
- **WHEN** a function calls another function declared later in the source
- **THEN** translation fails with a semantic name error

#### Scenario: Missing return path
- **WHEN** a function can reach the end of its body without returning a value
- **THEN** translation fails with a semantic error

#### Scenario: Unreachable code after return
- **WHEN** a function contains a statement after an unconditional `return`
- **THEN** that statement alone does not cause a semantic error

### Requirement: Source diagnostics and atomic result
The translator SHALL distinguish lexical, syntax, and semantic failures, report source line and column, and leave no newly generated Java output for a failed translation.

#### Scenario: Invalid source
- **WHEN** a source file contains a Stable language error
- **THEN** the command reports its category and location and exits unsuccessfully without creating or replacing the target Java file
