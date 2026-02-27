---
name: complete
description: Complete a line of code.
---
You are completing a single line of Java code within an existing file.

## Task
- Complete the current line using the provided context and the line prefix.
- Return exactly one line (no trailing explanations, no extra lines).

## Context
- File context before the line:
```java
{{before}}
```

- Line prefix (beginning of the current line; do not repeat it in the output):
```java
{{prefix}}
```

- File context after the line:
```java
{{after}}
```

## Constraints
- Follow the style and idioms of the surrounding code.
- Do not change public APIs unless explicitly requested.
- Prefer standard library; avoid new dependencies unless required.
- If multiple completions are valid, choose the simplest correct one.

## Output
Provide only the remainder of the line to append after {{prefix}}.
