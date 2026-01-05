<p align="center">
  <img src="https://github.com/haifengl/smile/blob/master/serve/llama.jpg" width="400"/>
</p>

---


# Smile Serve

Practical LLM Inference on JVM. A Java implementation of Llama 3 is available
in [`smile.llm.llama`](https://github.com/haifengl/smile/tree/master/deep/src/main/java/smile/llm/llama) package. The [`smile-deep`](https://github.com/haifengl/smile/tree/master/deep) module also
includes other deep learning models such as EfficientNet v2 for computer
vision. The inference server is implemented in Java with Quarkus.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./gradlew :serve:quarkusDev --jvm-args="--add-opens java.base/java.lang=ALL-UNNAMED"
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Packaging and running the application

The application can be packaged using:

```shell script
./gradlew :serve:build
```

It produces the `quarkus-run.jar` file in the `build/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `build/quarkus-app/lib/` directory.

The application is now runnable using `java -jar build/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./gradlew :serve:build -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar build/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./gradlew :serve:build -Dquarkus.native.enabled=true
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./gradlew :serve:build -Dquarkus.native.enabled=true -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./build/smile-serve-5.1.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/gradle-tooling>.
