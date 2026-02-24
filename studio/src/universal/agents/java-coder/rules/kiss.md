---
name: kiss-principle
description: Keep It Simple.
---
## KISS - Keep It Simple

> "The simplest solution is usually the best."

### Violation

```java
// ❌ BAD: Over-engineered for simple task
public class StringUtils {

    public boolean isEmpty(String str) {
        return Optional.ofNullable(str)
            .map(String::trim)
            .map(String::isEmpty)
            .orElseGet(() -> Boolean.TRUE);
    }
}
```

### Refactored

```java
// ✅ GOOD: Simple and clear
public class StringUtils {

    public boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    // Or use existing library
    // return StringUtils.isBlank(str);  // Apache Commons
    // return str == null || str.isBlank();  // Java 11+
}
```

### KISS Checklist

- Can a junior developer understand this in 30 seconds?
- Is there a simpler way using standard libraries?
- Am I adding complexity for edge cases that may never happen?
