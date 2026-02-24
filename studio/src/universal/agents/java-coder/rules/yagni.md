---
name: yagni-principle
description: You Aren't Gonna Need It.
---
## YAGNI - You Aren't Gonna Need It

> "Don't add functionality until it's necessary."

### Violation

```java
// ❌ BAD: Building for hypothetical future
public interface Repository<T, ID> {
    T findById(ID id);
    List<T> findAll();
    List<T> findAll(Pageable pageable);
    List<T> findAll(Sort sort);
    List<T> findAllById(Iterable<ID> ids);
    T save(T entity);
    List<T> saveAll(Iterable<T> entities);
    void delete(T entity);
    void deleteById(ID id);
    void deleteAll(Iterable<T> entities);
    void deleteAll();
    boolean existsById(ID id);
    long count();
    // ... 20 more methods "just in case"
}

// Current usage: only findById and save
```

### Refactored

```java
// ✅ GOOD: Only what's needed now
public interface UserRepository {
    Optional<User> findById(Long id);
    User save(User user);
}

// Add methods when actually needed, not before
```

### YAGNI Signs

- "We might need this later"
- "Let's make it configurable just in case"
- "What if we need to support X in the future?"
- Abstract classes with one implementation
