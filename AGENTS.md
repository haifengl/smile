# AGENTS.md - Multi-Project Java Guidelines

This file provides context for AI coding agents working on this multi-module Gradle project.

## 🏗 Build & Runtime Environment
- **Language:** Java 25
- **Build System:** Gradle 9.x (Kotlin DSL)
- **Testing:** JUnit 5

## 🚀 Build & Development Commands
Always use the Gradle Wrapper (`./gradlew`) to ensure version consistency.

- **Build everything:** `./gradlew build`
- **Build specific module:** `./gradlew :<module-name>:build -x test`
- **Run tests for a module:** `./gradlew :<module-name>:test`
- **Run specific test:** `./gradlew test --tests "com.example.ClassName.methodName"`
- **Run inference service:** `./gradlew :serve:quarkusDev --jvm-args="--add-opens java.base/java.lang=ALL-UNNAMED"`
- **Clean all modules:** `./gradlew clean`
- **Clean build:** `./gradlew clean build`
- **Check dependency tree:** `./gradlew :<module-name>:dependencies`

## 🏗 Project Structure (Multi-Module)
The project follows a hierarchical structure. Always check `base` before adding new utility classes.

- `settings.gradle.kts`: Multi-module definitions.
- `gradle/libs.versions.toml`: Centralized dependency management (Version Catalog).
- `buildSrc/`: Shared build logic across all modules, such as custom plugins, tasks, and configurations.
- `base/`: Common utilities, mathematical & statistical methods, linear algebra, data frames and IO operations, etc.
- `core/`: Core machine learning algorithms.
- `nlp/`: Natural language process libraries.
- `deep/`: Deep learning libraries.
- `plot/`: Data visualization libraries.
- `kotlin/`: Kotlin API with corresponding language paradigms.
- `serve/`: Machine learning inference service with Quarkus.

## 📝 Coding Standards
- **Style:** Follow Google Java Style Guide.
- **Records:** Prefer Java `record` for DTOs and immutable data carriers.
- **Null Safety:** Use `Optional<T>` for return types that may be empty; avoid returning `null`.
- **Logging:** Use SLF4J API for logging; avoid implementation-specific imports in library modules.

## 🧪 Testing Guidelines
- Use the **Given/When/Then** structure for all test methods.
- **Unit Tests:** Focus on single classes.
- **Resources:** Place test-specific data in src/test/resources within the relevant module.
- **Database:** Use Testcontainers for any tests requiring a real database.

## ⚠️ Dos and Don'ts
- **DO:** Check `build.gradle.kts` before adding new dependencies to avoid version conflicts.
- **DO:** Write JavaDocs for public API methods and complex logic.
- **Visibility:** Use protected or package-private visibility where possible to keep the module API clean.
- **No Circular Dependencies:** Do not create circular dependencies between modules.
