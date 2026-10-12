# AGENTS.md - SMILE Project Instructions

SMILE is a JVM machine learning framework (Java 25) across a multi-module Gradle + sbt
build. This file is the project context agents load at startup: the conventions and
gotchas the build files do **not** state. Commands, module layout, and coding standards
are below; everything else lives in the docs this file points to.

## 🚀 Build & Runtime Environment
- **Language:** Java 25
- **Build Systems:** Gradle 9.x (Kotlin DSL) for the JVM and service modules; sbt 2.x for `studio` and the Scala modules (version pinned in `project/build.properties`).
- **Testing:** JUnit 6

## 🏗 Module Layout
Two build systems, two source-of-truth files:

- `settings.gradle.kts` — Gradle modules: `base`, `core`, `deep`, `nlp`, `plot`, `spark`, `kotlin`, `serve`, `studio`.
- `build.sbt` — sbt modules: `base`, `core`, `deep`, `nlp`, `plot`, `json`, `scala`, `spark`, `kotlin`, `studio`.
- A module declared in both builds is intentional: Gradle builds it for the JVM/service path, sbt for the Studio/Scala path. Keep the two declarations in sync when you add a module to both.
- `gradle/libs.versions.toml` — version catalog for Gradle modules.
- `buildSrc/src/main/kotlin/buildlogic.*.gradle.kts` — shared convention plugins. JUnit, the Java 25 toolchain, test working directory, and the `-DexcludeTags` / `-DincludeTags` plumbing live in `buildlogic.java-common-conventions.gradle.kts`.

Each module below is marked with its build system:

- `base/` (Gradle + sbt): Common utilities, mathematical & statistical methods, linear algebra, data frames and IO operations, etc.
- `core/` (Gradle + sbt): Core machine learning algorithms.
- `deep/` (Gradle + sbt): Deep learning libraries.
- `nlp/` (Gradle + sbt): Natural language process libraries.
- `plot/` (Gradle + sbt): Data visualization libraries.
- `spark/` (Gradle + sbt): Apache Spark integration.
- `kotlin/` (Gradle + sbt): Kotlin API with corresponding language paradigms.
- `json/`, `scala/` (sbt): JSON and Scala APIs.
- `studio/` (Gradle + sbt): SMILE Studio, the agentic data science IDE (`smile.Main`). UI, notebook, kernel, or agent-panel questions: `studio/README.md`. `smile` CLI subcommands, launcher flags, or packaging: `studio/CLI.md`.
- `serve/` (Gradle): Machine learning inference service with Quarkus.

## ⌨️ Build & Development Commands

### Gradle Modules
Always use the Gradle Wrapper (`./gradlew`) to ensure version consistency.

- **Build everything:** `./gradlew build`
- **Build specific module:** `./gradlew :<module-name>:build -x test`
- **Run tests for a module:** `./gradlew :<module-name>:test`
- **Run specific test:** `./gradlew test --tests "com.example.ClassName.methodName"`
- **Run Studio application:** `./gradlew :studio:run`
- **Stage Studio application:** `./gradlew :studio:stage` (installs to `studio/build/install/smile`)
- **Run inference service:** `./gradlew :serve:quarkusDev --jvm-args="--add-opens java.base/java.lang=ALL-UNNAMED"`
- **Clean all modules:** `./gradlew clean`
- **Clean build:** `./gradlew clean build`
- **Check dependency tree:** `./gradlew :<module-name>:dependencies`

### Studio and Scala Modules (sbt)
Run sbt from the repository root: `studio` and the Scala modules are projects in the root build.

- **Stage Studio:** `sbt studio/stage`
- **Run staged Studio:** `target/out/jvm/scala-<version>/smile-studio/universal/stage/bin/smile`
- **Run Studio tests:** `sbt studio/test`
- **Run specific test:** `sbt "studio/testOnly smile.shell.JShellTest"`
- **Build Scala 2.13 variant:** `sbt ++2.13.18 scala/package`
- **Publish locally:** `sbt publishM2`

Studio gotchas:
- `studio` can be built and packaged with either Gradle (`./gradlew :studio:stage`, `:studio:run`) or sbt (`sbt studio/stage`). Note that in Gradle test runs, `ScalaKernelTest` is filtered out because `scala` and `json` modules are built via sbt.
- Staging bundles the Gradle-built Quarkus serve app and `base` test data, so run `./gradlew :serve:build` first.

## 📝 Coding Standards
- **Style:** Follow Google Java Style Guide.
- **Records:** Prefer Java `record` for DTOs and immutable data carriers.
- **Null Safety:** Use `Optional<T>` for return types that may be empty; avoid returning `null`.
- **Logging:** Use the SLF4J API in library modules; keep implementation-specific imports out. The `serve` module may use JBoss Logging as a Quarkus app.

## 🛠 Testing Guidelines
- Use the **Given/When/Then** structure for all test methods.
- **Unit Tests:** Focus on single classes.
- **Resources:** Place test-specific data in `src/test/resources` within the relevant module. Load fixtures through `smile.io.Paths.getTestData(...)`, which resolves against the `smile.home` system property (default `base/src/test/resources/`) — never hard-code a path to a fixture. Add new fixture data under `base/src/test/resources/data/` in the matching topic folder (`classification/`, `weka/`, `regression/`, `mnist/`, …).
- **Tag slow or environment-dependent tests.** Gate them with `@Tag("integration")` (real datasets / native libs / long runs), `@Tag("cuda")` (needs a GPU), or `@Tag("network")` (needs egress). CI runs `./gradlew test -DexcludeTags=integration`, so an ungated slow test becomes a flaky CI failure. Gated tests must skip cleanly via `Assumptions` when their environment is absent.
- **Database:** Use Testcontainers for any tests requiring a real database.

## 📐 Design Rules
- **Route by source of truth.** When the code and a doc disagree, the code wins and the doc is the bug to fix. Before changing behavior, edit the single authoritative site.
- **Land shared code in `base` first.** `base` is the foundation; every other module depends on it. Before writing a new utility, statistic, distance, kernel, or I/O helper, check whether `base` already has it — the package map is in `base/README.md`. Dependencies flow one way: `studio` → `deep` + `scala`; `serve` → `core` + `deep`; `nlp` → `core`; `kotlin` → `core` + `nlp`; `core`/`deep`/`plot` → `base`. Introducing a back-edge is a design error, not a build tweak.
- **Preserve the Java↔Scala↔Kotlin API surface.** The Scala and Kotlin shims in `scala/src/main/scala` and `kotlin/src/main/kotlin` mirror the Java API; when you change a public Java signature, update the shims in the same change. The Scala wrappers are maintained **by hand** — there is no generation step. The only Scala-version-specific sources are the 2.13 macros under `scala/src/main/scala-2.13` (`smile.plot.RenderMacro`); the default build targets Scala 3, so verify a Scala 2.13 variant build (`sbt ++2.13.18 scala/package`) after touching those.
- **Never hand-edit generated sources.** The FFM bindings under `base/src/main/java/smile/linalg/{arpack,blas,lapack}`, `deep/src/main/java/smile/onnx/foreign`, `deep/src/main/java/smile/onnx/genai/foreign`, and `deep/src/main/java/smile/torch` are generated by `jextract` (each file carries a `// Generated by jextract` header). Regenerate them; edit the generator input, not the output. These directories are also excluded from Javadoc in the Gradle build files.
- **Enable native access when you add a native call.** Any new FFM / JNI entry point means the JVM needs `--enable-native-access=ALL-UNNAMED` (plus `--add-opens=java.base/java.nio=ALL-UNNAMED` for on-heap/tensor access). The launchers and test configs already pass these flags; a new entry point without them fails at runtime, not at compile time.
- **Add every UI string to all five locales.** Studio strings live in `ResourceBundle` files under `studio/src/main/resources/smile/studio/**`, with bundles for `en_US`, `zh_CN`, `ja_JP`, `fr_FR`, and `es_ES`. A key added to the base `.properties` but not the localized bundles is a `MissingResourceException` at runtime (`SearchResourceBundleTest` pins this contract for the Find/Replace menu — extend that pattern for new UI).

## ⚠️ Dos and Don'ts
- **DO:** Check `build.gradle.kts` or `build.sbt` before adding new dependencies to avoid version conflicts. `base` already declares arrow (Parquet), Jackson, commons-csv, DuckDB, and Avro — reuse those rather than adding a parallel library, and prefer the version catalog (`gradle/libs.versions.toml`) for anything new.
- **DO:** Write JavaDocs for public API methods and complex logic.
- **Visibility:** Use protected or package-private visibility where possible to keep the module API clean.
- **No Circular Dependencies:** Do not create circular dependencies between modules.

## 🔥 Facts and Gotchas
- `iris.csv`, `iris.json`, `mnist.pt`, `net.pt`, `studio-cp.txt`, `create_boxplots.py` at the working-directory root, and the whole `data/` and `model/` trees, are **untracked scratch state** — never commit them and never treat their contents as fixtures. `data/`, `model/`, and `*.pt` are gitignored, but the root files (`iris.csv`, …) carry no ignore rule, so `git add -A` would sweep them in: **never `git add -A` in this repo.**
- The project version lives only in `build.sbt` (`version := ...`). The README badge and `serve` docs repeat version numbers; the build file is authoritative.
- The current working branch and recent commits are not recorded here — run `git rev-parse --abbrev-ref HEAD` and `git log --oneline -5` before assuming a base. Anything in this file that describes in-flight work is stale the moment it merges.
- Staging Studio bundles the Gradle-built Quarkus `serve` app and `base` test data, so build `serve` first or the staged package is incomplete. The staging artifacts under `target/` and `build/` are generated — never edit them.
- CI runs Gradle only. Studio and Scala-module changes need a local sbt run before you claim they pass.
- **Never kill every `java` process.** An agent working in this repo *is itself* a Java process, and so are the Gradle daemon, the sbt server, and the LSP/MCP servers the tools talk to. `taskkill /IM java.exe /F`, `pkill -f java`, `Get-Process java | Stop-Process`, `jps | ... kill`, or any equivalent "clean up all Java" sweep terminates the agent mid-run and can corrupt the sbt/Gradle caches. To stop a build, target it by name — the `sbt` launcher process (`taskkill /IM sbt.bat /F` on Windows), a specific PID, or `sbt -batch` with a timeout. If you only need the test JVM that outlived a run to release its file locks, that is the narrowest thing to kill, and only after confirming the PID is not the agent's own.
- **The one JVM you must never kill is the Studio host.** It is the process whose entry class is `smile.studio.Main` (launched from `target/out/jvm/u/smile-studio/universal/stage/bin/...`). It hosts the agent session, so killing it terminates you mid-run. Identify it by command line before killing anything: `Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Select ProcessId, CommandLine` and look for `smile.Main` / `smile.studio.Main`. Everything else — Gradle daemons, sbt servers, JShell `RemoteExecutionControl` children, leftover test JVMs — is fair game to target by PID when you need a file lock released.
- **A running Studio holds the staged JBR open.** Because the host runs from `stage/jbr/bin/java.exe`, it locks `stage/jbr/bin/nio.dll`, and `sbt studio/stage` fails with `FileNotFoundException: ...stage\jbr\bin\nio.dll (The process cannot access the file because it is being used by another process)`. Since that lock is held by the host you cannot kill, ask the user to close Studio (or stage to a different output) rather than trying to clear it. Separately, `studio/clean` deletes the staged JBR, which breaks any `JAVA_HOME` pointing at it — restore it with `sbt studio/stage` before running sbt again.

## 📚 References — reach for these when the work calls for it

Every topic below has a dedicated doc; this file deliberately does not duplicate them. The
root [README.md](./README.md) holds the module map, installation, and quick start.

| When the work touches … | Read |
|---|---|
| Module layout, coding standards | [CONTRIBUTING.md](./CONTRIBUTING.md) |
| DataFrames, formulas, transforms, I/O, datasets, math/stat | [base/README.md](./base/README.md) and its topic guides (`DATA_FRAME.md`, `FORMULA.md`, `DATA_IO.md`, …) |
| Classifiers, regressors, clustering, manifold, validation, HPO | [core/README.md](./core/README.md) and its topic guides |
| Tensors, layers, loss, optimizer, model, metrics | [deep/README.md](./deep/README.md) |
| LLMs — tokenizer, LLaMA-3, Qwen3.5, inference engine, KV cache, quantization | [deep/LLM.md](./deep/LLM.md) |
| Computer vision — transforms, `ImageDataset`, EfficientNet-V2, ImageNet | [deep/VISION.md](./deep/VISION.md) |
| ONNX Runtime & GenAI inference | [deep/ONNX.md](./deep/ONNX.md), [deep/ONNX_GENAI.md](./deep/ONNX_GENAI.md) |
| Interactive or Vega-Lite charts | [plot/README.md](./plot/README.md), [plot/VEGA.md](./plot/VEGA.md) |
| Tokenization, POS, stemming, relevance | [nlp/README.md](./nlp/README.md) |
| Idiomatic Scala / Kotlin usage | [scala/README.md](./scala/README.md), [kotlin/README.md](./kotlin/README.md) |
| Spark pipelines | [spark/README.md](./spark/README.md) |
| The inference server (REST, ONNX, LLM chat) | [serve/README.md](./serve/README.md) |
| Studio UI, notebooks, kernels, agent panel; `smile` CLI subcommands and flags | [studio/README.md](./studio/README.md), [studio/CLI.md](./studio/CLI.md) |
| Agent skills (this file's format), slash commands, Studio's AI harness, LLM clients, MCP — or product strategy | [.smile/SMILE.md](./.smile/SMILE.md) → `../ioa/agent/src`, `../halo` (closed source) |
