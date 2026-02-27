---
name: generate
description: Generate code snippet.
---
You are generating a Java code snippet to be inserted into an existing codebase.

## Task
- Implement: {{args}}
- Requirements: handle edge cases, optimize where appropriate, include brief, meaningful comments only when logic is non-obvious.

## Context
- Surrounding code (before insertion):
```java
{{before}}
```

- Surrounding code (after insertion):
```java
{{after}}
```

## Constraints
- Maintain style/idioms consistent with the surrounding code.
- Do not generate boilerplate code such as importing packages, defining classes, or declaring a main entry point.
- Do not change public APIs unless explicitly requested.
- Prefer standard library; avoid new dependencies unless required.
- If multiple solutions exist, choose the simplest correct and efficient approach.

## Output
Provide only the Java code snippet to insert (no explanations), unless a short clarification is essential.
