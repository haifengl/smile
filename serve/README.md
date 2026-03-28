<p align="center">
  <img src="https://github.com/haifengl/smile/blob/master/serve/llama.jpg" width="400"/>
</p>

---


# Smile Serve

Practical ML and LLM Inference on JVM. A Java implementation of Llama 3 is
available in [`smile.llm.llama`](https://github.com/haifengl/smile/tree/master/deep/src/main/java/smile/llm/llama)
package. The [`smile-deep`](https://github.com/haifengl/smile/tree/master/deep) module also
includes other deep learning models such as EfficientNet v2 for computer
vision. The inference server is implemented in Java with Quarkus.

## Running the inference server with Docker

To get started using the Docker image, please use the commands below.

```shell script
docker run -it -v /path/to/model/folder:/model -p 8888:8080 ghcr.io/haifengl/smile-serve:latest
```

## Running the inference server in dev mode

You can run the service in dev mode that enables live coding using:

```shell script
./gradlew :serve:quarkusDev --jvm-args="--add-opens java.base/java.lang=ALL-UNNAMED"
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Packaging and running the inference server

The application can be packaged using:

```shell script
./gradlew :serve:build
```

It produces the `quarkus-run.jar` file in the `build/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `build/quarkus-app/lib/` directory.

The service is now runnable using
```shell script
java --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.nio=ALL-UNNAMED \
     --enable-native-access ALL-UNNAMED \
     -jar build/quarkus-app/quarkus-run.jar
```

By default, the service runs on the port 8080. To customize the port,
use the `-D` flag on command line. For example,
```shell script
java --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.nio=ALL-UNNAMED \
     --enable-native-access ALL-UNNAMED \
     -Dquarkus.http.port=3801 \
     -jar build/quarkus-app/quarkus-run.jar
```

By default, the service loads all model files (`*.sml`) from the folder
`../model`. You may specify system property `smile.serve.model` to a
`.sml` file or a folder to override the model location. You may list
the available models with the endpoint `/v1/models`.
```shell script
curl http://localhost:8080/v1/models
```
The model metadata can be queried by `/v1/models/{modelId}`.
```shell script
curl http://localhost:8080/v1/models/iris_random_forest-1
```
To send an inference request, use `POST`
```shell script
curl -X POST -H "Content-Type: application/json" http://localhost:8080/v1/models/iris_random_forest-1 \
        -d '{
          "sepallength": 5.1,
          "sepalwidth": 3.5,
          "petallength": 1.4,
          "petalwidth": 0.2
        }'
```
Or use the endpoint `/v1/models/{modelId}/stream` for stream processing.
```shell script
cat iris.txt | curl -H "Content-Type: text/plain" -X POST --data-binary @- http://localhost:8080/v1/models/iris_random_forest-1/stream
```

If you want to build an _über-jar_, execute the following command:

```shell script
./gradlew :serve:build -Dquarkus.package.jar.type=uber-jar
```

The service, packaged as an _über-jar_, is now runnable using `java -jar build/*-runner.jar`.

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
