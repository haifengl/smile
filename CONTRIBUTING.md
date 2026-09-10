# How to contribute to SMILE
Welcome! If you are interested in contributing to SMILE, reporting issues,
or just getting in touch with the folks who work on SMILE, this guide is
for you.

One of the easiest ways to contribute is to participate in discussions
and discuss issues. You can also contribute by opening an issue and
submitting a pull request with code changes.

## Build
To build SMILE from source, please first install Java 25, Scala 3
and SBT 2.0+. Then clone the repo and build the package:

```bash
git clone https://github.com/haifengl/smile.git
cd smile
# compile all packages
sbt package
# run unit tests
sbt test
```

To build SMILE Serve, use Gradle (Quarkus runtime):
```bash
# Build SMILE Serve
./gradlew :serve:build
# Or run in development mode
./gradlew :serve:quarkusDev
```

To build with Scala 2.13, run
```bash
sbt ++2.13.18 scala/package
```

To publish SMILE packages locally:
```bash
sbt publishM2
```

To play with the latest SMILE Studio or REPL:
```bash
git pull
bin/smile.sh

# Or run the shell directly:
sbt studio/stage
cd target/out/jvm/scala-3.9.0/smile-studio/universal/stage
./bin/smile shell
```

## Open an issue
For bugs, issues, or feature suggestions, please log a new issue in the GitHub repo.
GitHub supports [Markdown](https://help.github.com/categories/writing-on-github/), so please check your formatting before submitting.

## Other discussions
For general "how-to" questions and guidance on building applications with SMILE, please ask on [Stack Overflow](https://stackoverflow.com/questions/tagged/smile-ai) tagged with `smile-ai`.

## Contributing code and content
We welcome contributions from the community. Please follow these guidelines to ensure your PR can be reviewed and merged efficiently.

### Communication
- **Feature work:** Before starting significant new features, please open an issue describing the proposed change so we can coordinate design and ensure it aligns with the project roadmap.
- **Bug fixes and small patches:** Open an issue for tracking, or link an existing issue directly in your pull request.

### Development process
- Fork the repository.
- Create a feature or fix branch.
- Ensure test suites pass before submitting.
- Submit a pull request with a descriptive title and reference the related issue.

We reserve the right to close pull requests that have become stale or inactive; they can be reopened whenever work resumes.

### Contributor License Agreement (CLA)
To ensure that SMILE remains freely available as open-source software while enabling commercial licensing, ongoing maintenance, and enterprise distribution, all contributors must agree to the SMILE Contributor License Agreement.

By submitting a pull request, patch, or other contribution to the SMILE project, you confirm that:
- Your contribution complies with and is licensed under the terms of the **[SMILE Contributor License Agreement (CLA)](./CLA.md)**.
- You have the legal authority and any necessary employer approvals to grant these rights.

Please review **[CLA.md](./CLA.md)** for full terms, including copyright and patent grants.

## Code of Conduct
To ensure a welcoming and productive environment, all contributors and participants are expected to uphold the [Code of Conduct](./CODE_OF_CONDUCT.md).
