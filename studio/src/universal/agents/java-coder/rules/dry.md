---
name: dry-principle
description: Don't Repeat Yourself.
---
## DRY - Don't Repeat Yourself

> "Every piece of knowledge must have a single, unambiguous representation in the system."

### Violation

```java
// ❌ BAD: Same validation logic repeated
public class UserController {

    public void createUser(UserRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new ValidationException("Email is required");
        }
        if (!request.getEmail().contains("@")) {
            throw new ValidationException("Invalid email format");
        }
        // ... create user
    }

    public void updateUser(UserRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new ValidationException("Email is required");
        }
        if (!request.getEmail().contains("@")) {
            throw new ValidationException("Invalid email format");
        }
        // ... update user
    }
}
```

### Refactored

```java
// ✅ GOOD: Single source of truth
public class EmailValidator {

    public void validate(String email) {
        if (email == null || email.isBlank()) {
            throw new ValidationException("Email is required");
        }
        if (!email.contains("@")) {
            throw new ValidationException("Invalid email format");
        }
    }
}

public class UserController {
    private final EmailValidator emailValidator;

    public void createUser(UserRequest request) {
        emailValidator.validate(request.getEmail());
        // ... create user
    }

    public void updateUser(UserRequest request) {
        emailValidator.validate(request.getEmail());
        // ... update user
    }
}
```

### DRY Exceptions

Not all duplication is bad. Avoid premature abstraction:

```java
// These look similar but serve different purposes - OK to duplicate
public BigDecimal calculateShippingCost(Order order) {
    return order.getWeight().multiply(SHIPPING_RATE);
}

public BigDecimal calculateInsuranceCost(Order order) {
    return order.getValue().multiply(INSURANCE_RATE);
}
// Don't force these into one method - they'll evolve differently
```
