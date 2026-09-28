# AGENTS.md - Multi-Project Java Guidelines

This file provides context for AI coding agents working on this multi-build project:
Gradle for the JVM/service modules and sbt for Studio and the Scala modules.

## 🚀 Build & Runtime Environment
- **Language:** Java 25
- **Build Systems:** Gradle 9.x (Kotlin DSL) for the JVM and service modules; sbt 2.x for `studio` and the Scala modules (version pinned in `project/build.properties`).
- **Testing:** JUnit 6

## ⌨️ Build & Development Commands

### Gradle Modules
Always use the Gradle Wrapper (`./gradlew`) to ensure version consistency.

- **Build everything:** `./gradlew build`
- **Build specific module:** `./gradlew :<module-name>:build -x test`
- **Run tests for a module:** `./gradlew :<module-name>:test`
- **Run specific test:** `./gradlew test --tests "com.example.ClassName.methodName"`
- **Run inference service:** `./gradlew :serve:quarkusDev --jvm-args="--add-opens java.base/java.lang=ALL-UNNAMED"`
- **Clean all modules:** `./gradlew clean`
- **Clean build:** `./gradlew clean build`
- **Check dependency tree:** `./gradlew :<module-name>:dependencies`

### Studio and Scala Modules (sbt)
Run sbt from the repository root: `studio` is a project of the root build, not a standalone build.

- **Stage Studio:** `sbt studio/stage`
- **Run staged Studio:** `target/out/jvm/scala-<version>/smile-studio/universal/stage/bin/smile`
- **Run Studio tests:** `sbt studio/test`
- **Run specific test:** `sbt "studio/testOnly smile.shell.JShellTest"`
- **Build Scala 2.13 variant:** `sbt ++2.13.18 scala/package`
- **Publish locally:** `sbt publishM2`

Studio gotchas:
- Edit `studio/build.sbt` for Studio packaging; `studio/build.gradle.kts` is stale and unused. Shared settings come from the root `build.sbt` (`javaSettings ++ scalaSettings`, `.dependsOn(deep, scala)`).
- Staging bundles the Gradle-built Quarkus serve app and `base` test data, so run `./gradlew :serve:build` first.
- CI runs Gradle only, so Studio changes need a local `sbt studio/test`.

## 🏗 Project Structure (Multi-Module)
The project follows a hierarchical structure. Always check `base` before adding new utility classes.
Each module below is marked with its build system.

- `settings.gradle.kts`: Gradle module definitions (`studio` is intentionally excluded).
- `build.sbt`: Root sbt build (Studio and Scala modules); `project/build.properties` pins the sbt 2.x version.
- `gradle/libs.versions.toml`: Centralized dependency management (Version Catalog).
- `buildSrc/`: Shared build logic across all modules, such as custom plugins, tasks, and configurations.
- `base/` (Gradle + sbt): Common utilities, mathematical & statistical methods, linear algebra, data frames and IO operations, etc.
- `core/` (Gradle + sbt): Core machine learning algorithms.
- `nlp/` (Gradle + sbt): Natural language process libraries.
- `deep/` (Gradle + sbt): Deep learning libraries.
- `plot/` (Gradle + sbt): Data visualization libraries.
- `kotlin/` (Gradle + sbt): Kotlin API with corresponding language paradigms.
- `json/`, `scala/`, `spark/` (sbt): JSON, Scala, and Spark APIs.
- `studio/` (sbt): SMILE Studio, the agentic data science IDE (`smile.Main`). UI, notebook, kernel, or agent-panel questions: `studio/README.md`. `smile` CLI subcommands, launcher flags, or packaging: `studio/CLI.md`.
- `serve/` (Gradle): Machine learning inference service with Quarkus.

## 📝 Coding Standards
- **Style:** Follow Google Java Style Guide.
- **Records:** Prefer Java `record` for DTOs and immutable data carriers.
- **Null Safety:** Use `Optional<T>` for return types that may be empty; avoid returning `null`.
- **Logging:** Use SLF4J API for logging; avoid implementation-specific imports in library modules. The `serve` module can use JBoss Logging as it is a Quarkus app.

## 🛠 Testing Guidelines
- Use the **Given/When/Then** structure for all test methods.
- **Unit Tests:** Focus on single classes.
- **Resources:** Place test-specific data in src/test/resources within the relevant module.
- **Database:** Use Testcontainers for any tests requiring a real database.

## ⚠️ Dos and Don'ts
- **DO:** Check `build.gradle.kts` or `build.sbt` before adding new dependencies to avoid version conflicts.
- **DO:** Write JavaDocs for public API methods and complex logic.
- **Visibility:** Use protected or package-private visibility where possible to keep the module API clean.
- **No Circular Dependencies:** Do not create circular dependencies between modules.
