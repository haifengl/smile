# SMILE Serve User Guide

<p align="center">
  <img src="https://github.com/haifengl/smile/blob/master/serve/llama.jpg" width="400"/>
</p>

SMILE Serve is a production-ready inference server built on [Quarkus](https://quarkus.io/)
that brings together three complementary inference capabilities on the JVM:

| Capability | API prefix | Description                                                    |
|---|---|----------------------------------------------------------------|
| **Classic ML** | `/api/v1/models` | Serialized SMILE models (`.sml`) — classifiers and regressors |
| **ONNX Runtime** | `/api/v1/onnx` | Any model in the ONNX open format (`.onnx`)                    |
| **LLM Chat** | `/api/v1/chat` | Llama 3 chat completions with conversation persistence         |

A React-based web UI is bundled and served from the same process.

---

## Table of Contents

1. [Quick Start with Docker](#1-quick-start-with-docker)
2. [Building and Running](#2-building-and-running)
   - [Dev Mode](#21-dev-mode)
   - [Packaging as a JAR](#22-packaging-as-a-jar)
   - [Uber-JAR](#23-uber-jar)
   - [Native Executable](#24-native-executable)
3. [Configuration Reference](#3-configuration-reference)
4. [Classic ML Inference API](#4-classic-ml-inference-api)
   - [Model Format](#41-model-format)
   - [List Models](#42-list-models)
   - [Get Model Metadata](#43-get-model-metadata)
   - [Single Inference (JSON)](#44-single-inference-json)
   - [Streaming Inference (CSV / JSON-lines)](#45-streaming-inference-csv--json-lines)
   - [Model IDs](#46-model-ids)
5. [ONNX Inference API](#5-onnx-inference-api)
   - [Model Format](#51-model-format)
   - [List ONNX Models](#52-list-onnx-models)
   - [Get ONNX Model Info](#53-get-onnx-model-info)
   - [Single Inference (JSON)](#54-single-inference-json)
   - [Streaming Inference](#55-streaming-inference)
   - [Tensor Types and Shape Resolution](#56-tensor-types-and-shape-resolution)
6. [LLM Chat API](#6-llm-chat-api)
   - [Chat Completions](#61-chat-completions)
   - [Conversation History API](#62-conversation-history-api)
7. [Web UI](#7-web-ui)
8. [Database](#8-database)
9. [Testing](#9-testing)

---

## 1. Quick Start with Docker

The fastest way to run SMILE Serve is via the pre-built Docker image.
Mount a local directory containing your model files and map the port:

```shell
docker run -it \
  -v /path/to/model/folder:/model \
  -p 8888:8080 \
  ghcr.io/haifengl/smile-serve:latest
```

The service starts on port 8080 inside the container (mapped to 8888 on the host).
Place your `.sml` and `.onnx` model files in `/path/to/model/folder`; they are
discovered automatically at startup.

---

## 2. Building and Running

All commands use the Gradle wrapper from the project root.

### 2.1 Dev Mode

Live-reload development mode — changes to Java sources are reflected without
restarting. The Quarkus Dev UI is available at <http://localhost:8888/q/dev/>.

```shell
./gradlew :serve:quarkusDev \
  --jvm-args="--add-opens java.base/java.lang=ALL-UNNAMED"
```

> The `--add-opens` flags are required by ONNX Runtime's Foreign Function Interface.
> The dev-mode HTTP port defaults to **8888** (configured via `%dev.quarkus.http.port`).

### 2.2 Packaging as a JAR

```shell
./gradlew :serve:build
```

This produces a Quarkus layered application in `build/quarkus-app/`.
The entry point is `build/quarkus-app/quarkus-run.jar`; the dependencies
live in `build/quarkus-app/lib/` and must be distributed together.

Run it with:

```shell
java \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  --add-opens java.base/java.nio=ALL-UNNAMED \
  --enable-native-access ALL-UNNAMED \
  -jar build/quarkus-app/quarkus-run.jar
```

To run on a custom port:

```shell
java \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  --add-opens java.base/java.nio=ALL-UNNAMED \
  --enable-native-access ALL-UNNAMED \
  -Dquarkus.http.port=3801 \
  -jar build/quarkus-app/quarkus-run.jar
```

### 2.3 Uber-JAR

A single self-contained JAR (slower to start, simpler to deploy):

```shell
./gradlew :serve:build -Dquarkus.package.jar.type=uber-jar
java \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  --add-opens java.base/java.nio=ALL-UNNAMED \
  --enable-native-access ALL-UNNAMED \
  -jar build/smile-serve-runner.jar
```

### 2.4 Native Executable

Compile to a native binary with GraalVM (sub-millisecond startup, lower memory):

```shell
./gradlew :serve:build -Dquarkus.native.enabled=true
./build/smile-serve-*-runner
```

Without a local GraalVM installation, use a Docker-based build:

```shell
./gradlew :serve:build \
  -Dquarkus.native.enabled=true \
  -Dquarkus.native.container-build=true
```

See the [Quarkus native build guide](https://quarkus.io/guides/gradle-tooling) for details.

---

## 3. Configuration Reference

Configuration is managed in `src/main/resources/application.properties`.
Quarkus profile prefixes (`%dev.`, `%test.`) override the base values in
the corresponding profiles.

| Property | Default | Description |
|---|---|---|
| `quarkus.http.port` | `8080` | HTTP listen port (`%dev` default: `8888`) |
| `quarkus.rest.path` | `/api/v1` | Global REST path prefix |
| `smile.serve.model` | `../model` | Path to a `.sml` file or directory of `.sml` files |
| `smile.onnx.model` | `../model` | Path to a `.onnx` file or directory of `.onnx` files |
| `smile.chat.model` | `../model/Llama3.1-8B-Instruct` | Directory containing the Llama model |
| `smile.chat.tokenizer` | `../model/Llama3.1-8B-Instruct/tokenizer.model` | SentencePiece tokenizer path |
| `smile.chat.max_seq_len` | `4096` | Maximum sequence length in tokens |
| `smile.chat.max_batch_size` | `1` | Maximum generation batch size |
| `smile.chat.device` | `0` | GPU device index (`%dev` default: `7`) |
| `quarkus.datasource.db-kind` | `postgresql` | Database backend for chat history |
| `quarkus.datasource.jdbc.url` | `jdbc:postgresql://localhost:5432/smile` | JDBC connection URL |
| `quarkus.hibernate-orm.active` | `false` | Enable ORM (set `true` when database is available) |

**Override at runtime** with `-D` system properties, for example:

```shell
java ... -Dsmile.serve.model=/data/models/rf_classifier.sml -jar quarkus-run.jar
```

---

## 4. Classic ML Inference API

### 4.1 Model Format

Classic ML models are serialized Java objects saved in `.sml` files by the
SMILE `smile.model.Model` framework. They carry:

- The trained algorithm (random forest, SVM, gradient boost, etc.)
- The input feature schema (field names and data types)
- Training / validation metrics
- Optional metadata tags (`id`, `version`, user-defined properties)

At startup, `InferenceService` scans the path specified by the property
`smile.serve.model`. If the path is a regular `.sml` file only that model
is loaded; if it is a directory every `.sml` file in the directory is loaded.

### 4.2 List Models

Returns the IDs of all loaded models in alphabetical order.

```
GET /api/v1/models
```

**Example:**

```shell
curl http://localhost:8080/api/v1/models
```

```json
["iris_random_forest-1", "titanic_logistic-2"]
```

### 4.3 Get Model Metadata

Returns the algorithm name, input schema, and tags for a model.

```
GET /api/v1/models/{id}
```

**Example:**

```shell
curl http://localhost:8080/api/v1/models/iris_random_forest-1
```

```json
{
  "id": "iris_random_forest-1",
  "algorithm": "random-forest",
  "schema": {
    "petallength": { "type": "float", "nullable": false },
    "petalwidth":  { "type": "float", "nullable": false },
    "sepallength": { "type": "float", "nullable": false },
    "sepalwidth":  { "type": "float", "nullable": false }
  },
  "tags": {
    "smile.random_forest.trees": "200"
  }
}
```

The `schema` object lists every input feature in alphabetical order — this
is the **column order** used by the CSV streaming endpoint.

### 4.4 Single Inference (JSON)

Send one sample as a JSON object and receive the prediction synchronously.

```
POST /api/v1/models/{id}
Content-Type: application/json
```

The request body is a flat JSON object whose keys are the feature names
defined in the model schema. **All non-nullable fields are required.**

**Classification example (iris):**

```shell
curl -X POST http://localhost:8080/api/v1/models/iris_random_forest-1 \
  -H "Content-Type: application/json" \
  -d '{
    "sepallength": 5.1,
    "sepalwidth":  3.5,
    "petallength": 1.4,
    "petalwidth":  0.2
  }'
```

```json
{
  "prediction": 0,
  "probabilities": [0.960, 0.021, 0.019]
}
```

- `prediction` — the predicted class label (integer) or regression value (float).
- `probabilities` — posterior class probabilities for **soft classifiers**
  (e.g. random forest, logistic regression). Absent for hard classifiers and
  regressors.

**Error responses:**

| HTTP | Cause |
|---|---|
| `400 Bad Request` | Missing required field, or malformed JSON |
| `404 Not Found` | Unknown model ID |

### 4.5 Streaming Inference (CSV / JSON-lines)

Process many samples in a single request. The server returns results as a
[Server-Sent Events](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events)
stream — one `data:` line per input sample.

```
POST /api/v1/models/{id}/stream
Content-Type: text/plain          ← CSV mode
Content-Type: application/json   ← JSON-lines mode
```

#### CSV mode (`text/plain`)

Each non-blank line is a comma-separated row of feature values in the **same
column order as the model schema** (alphabetical by field name, as shown by
`GET /api/v1/models/{id}`).

```shell
cat iris.csv | curl -X POST \
  -H "Content-Type: text/plain" \
  --data-binary @- \
  http://localhost:8080/api/v1/models/iris_random_forest-1/stream
```

Where `iris.csv` might contain:

```
5.1,3.5,1.4,0.2
6.7,3.0,5.2,2.3
5.8,2.7,4.1,1.0
```

The response stream (SSE format):

```
data: 0 0.960 0.021 0.019

data: 2 0.012 0.051 0.937

data: 1 0.031 0.752 0.217
```

#### JSON-lines mode (`application/json`)

Each non-blank line must be a complete JSON object (one per line).
This is more verbose but supports named fields in any order.

```shell
cat iris.jsonl | curl -X POST \
  -H "Content-Type: application/json" \
  --data-binary @- \
  http://localhost:8080/api/v1/models/iris_random_forest-1/stream
```

Where `iris.jsonl` contains:

```json
{"sepallength":5.1,"sepalwidth":3.5,"petallength":1.4,"petalwidth":0.2}
{"sepallength":6.7,"sepalwidth":3.0,"petallength":5.2,"petalwidth":2.3}
```

### 4.6 Model IDs

A model's ID is constructed as `<name>-<version>` from the model's embedded
metadata tags (`smile.model.Model.ID` and `smile.model.Model.VERSION`).
If those tags are absent, the file name stem is used as the name and `"1"` as
the version. For example, a file named `iris_random_forest.sml` with no ID
tag gets the ID `iris_random_forest-1`.

---

## 5. ONNX Inference API

The ONNX endpoint exposes any model in the
[ONNX open format](https://onnx.ai/) through SMILE's native ONNX Runtime
binding (`smile.onnx`). This covers models exported from PyTorch, TensorFlow,
scikit-learn (via `sklearn-onnx`), and many other frameworks.

### 5.1 Model Format

At startup, `OnnxService` scans the folder specified by the property
`smile.onnx.model`. Every `.onnx` file found is loaded into an
`InferenceSession`. The model ID is the file name without
the `.onnx` extension (e.g., `resnet50.onnx` → ID `resnet50`).

### 5.2 List ONNX Models

```
GET /api/v1/onnx
```

```shell
curl http://localhost:8080/api/v1/onnx
```

```json
["resnet50", "sentiment_bert"]
```

### 5.3 Get ONNX Model Info

Returns graph metadata and the typed, shaped input/output node descriptors.

```
GET /api/v1/onnx/{id}
```

```shell
curl http://localhost:8080/api/v1/onnx/resnet50
```

```json
{
  "id": "resnet50",
  "graphName": "ResNet50",
  "description": "Image classification model",
  "version": 1,
  "inputs": [
    {
      "name": "input",
      "onnxType": "TENSOR",
      "elementType": "FLOAT",
      "shape": [1, 3, 224, 224]
    }
  ],
  "outputs": [
    {
      "name": "output",
      "onnxType": "TENSOR",
      "elementType": "FLOAT",
      "shape": [1, 1000]
    }
  ],
  "customMeta": {}
}
```

A shape value of `-1` means that dimension is **dynamic** (determined at
inference time from the input data).

### 5.4 Single Inference (JSON)

```
POST /api/v1/onnx/{id}
Content-Type: application/json
```

The request body is a JSON object mapping each **input name** to a **flat
JSON array** of numbers. The server constructs the required ORT tensor from the
declared element type and shape.

**Example — image classification (resnet50, 1×3×224×224 = 150528 floats):**

```shell
curl -X POST http://localhost:8080/api/v1/onnx/resnet50 \
  -H "Content-Type: application/json" \
  -d '{"input": [0.485, 0.456, 0.406, ...]}'
```

Response — a JSON object mapping each **output name** to a flat array:

```json
{
  "output": [0.001, 0.002, 0.872, 0.003, ...]
}
```

**Multi-input model example:**

```shell
curl -X POST http://localhost:8080/api/v1/onnx/bert_classifier \
  -H "Content-Type: application/json" \
  -d '{
    "input_ids":      [101, 2054, 2003, 1996, 3007, 1997, 2605, 1029, 102],
    "attention_mask": [1,   1,    1,    1,    1,    1,    1,    1,    1  ],
    "token_type_ids": [0,   0,    0,    0,    0,    0,    0,    0,    0  ]
  }'
```

**Supported input element types:**

| ONNX type | JSON values | ORT type |
|---|---|---|
| `FLOAT` | numbers | `float[]` |
| `DOUBLE` | numbers | `double[]` |
| `INT32` | integers | `int[]` |
| `INT64` | integers | `long[]` |
| `INT8` / `UINT8` / `BOOL` | integers (0/1 for bool) | `byte[]` |

**Error responses:**

| HTTP | Cause |
|---|---|
| `400 Bad Request` | Missing input, wrong element count, non-numeric values |
| `404 Not Found` | Unknown model ID |

### 5.5 Streaming Inference

Identical in structure to the classic ML streaming endpoint but returns
JSON objects:

```
POST /api/v1/onnx/{id}/stream
Content-Type: text/plain          ← CSV floats for single-input models
Content-Type: application/json   ← JSON-lines for multi-input models
```

**CSV (single-input models only):**

```shell
cat features.csv | curl -X POST \
  -H "Content-Type: text/plain" \
  --data-binary @- \
  http://localhost:8080/api/v1/onnx/my_classifier/stream
```

Each response line is a compact JSON object:

```
data: {"output":[0.02,0.95,0.03]}

data: {"output":[0.88,0.07,0.05]}
```

**JSON-lines (any number of inputs):**

```shell
cat samples.jsonl | curl -X POST \
  -H "Content-Type: application/json" \
  --data-binary @- \
  http://localhost:8080/api/v1/onnx/bert_classifier/stream
```

### 5.6 Tensor Types and Shape Resolution

The server automatically resolves the ORT tensor shape from the model's
declared input shape and the actual array length:

- **Fully static shape** (no `-1` dimensions) — the array length must exactly
  match the product of all dimensions. A mismatch returns HTTP 400.
- **Single dynamic dimension** — the unknown dimension is inferred as
  `arrayLength / product(staticDimensions)`. For example, a declared shape
  `[-1, 3, 224, 224]` with 150528 elements resolves to `[1, 3, 224, 224]`.
- **Multiple dynamic dimensions** — the shape is set to `[1, arrayLength]`.
- **No shape info** — the shape is set to `[1, arrayLength]`.

---

## 6. LLM Chat API

SMILE Serve includes a Java implementation of
[Llama 3](https://github.com/haifengl/smile/tree/master/deep/src/main/java/smile/llm/llama)
for on-premise LLM inference. The chat API is designed to be compatible with
the OpenAI Chat Completions interface.

The LLM is optional: if the path specified by the property `smile.chat.model`
does not exist on the file system, `ChatService` starts in an *unavailable*
state and every request to the chat endpoints returns **HTTP 503 Service Unavailable**.

### 6.1 Chat Completions

```
POST /api/v1/chat/completions
Content-Type: application/json
```

Tokens are streamed back as Server-Sent Events. The conversation (user
message + assistant reply) is automatically persisted to the configured
database after generation finishes.

**Request body fields (`snake_case`):**

| Field | Type | Default | Description |
|---|---|---|---|
| `messages` | `Message[]` | *required* | Ordered dialog turns |
| `conversation` | `Long` | `null` | Existing conversation ID to append to |
| `max_tokens` | `int` | `2048` | Maximum new tokens to generate |
| `temperature` | `double` | `0.6` | Sampling temperature (higher = more random) |
| `top_p` | `double` | `0.9` | Nucleus-sampling threshold |
| `logprobs` | `boolean` | `false` | Include log-probabilities |
| `seed` | `long` | `0` | Random seed (0 = non-deterministic) |
| `stream` | `boolean` | `true` | Reserved; always streams |

Each `Message` has a `role` (`system`, `user`, or `assistant`) and `content`.

**Example — single-turn:**

```shell
curl -X POST http://localhost:8080/api/v1/chat/completions \
  -H "Content-Type: application/json" \
  -N \
  -d '{
    "messages": [
      {"role": "system",  "content": "You are a helpful assistant."},
      {"role": "user",    "content": "What is the capital of France?"}
    ],
    "max_tokens": 256,
    "temperature": 0.7
  }'
```

The response is an SSE stream of plain-text token chunks ending when
generation is complete.

**Example — continue a previous conversation:**

```shell
curl -X POST http://localhost:8080/api/v1/chat/completions \
  -H "Content-Type: application/json" \
  -N \
  -d '{
    "conversation": 42,
    "messages": [
      {"role": "user", "content": "What about Germany?"}
    ]
  }'
```

### 6.2 Conversation History API

Chat history is stored in a relational database (PostgreSQL in production,
SQLite in dev mode). The API base path is `/api/v1/conversations`.

#### List conversations

```
GET /api/v1/conversations?pageIndex=0&pageSize=25
```

Returns conversations in reverse-chronological order (newest first).
Pagination parameters default to page 0 with 25 records per page.

```shell
curl "http://localhost:8080/api/v1/conversations?pageSize=10"
```

#### Get a single conversation

```
GET /api/v1/conversations/{id}
```

Returns the conversation record (metadata only, no messages). Returns 404 if
the ID does not exist.

#### Get conversation messages

```
GET /api/v1/conversations/{id}/items?pageIndex=0&pageSize=25
```

Returns the individual message turns (`role` + `content` + `createdAt`)
in chronological order.

```shell
curl http://localhost:8080/api/v1/conversations/42/items
```

```json
[
  { "id": 1, "conversationId": 42, "role": "user",      "content": "What is the capital of France?", "createdAt": "2026-04-15T10:00:00Z" },
  { "id": 2, "conversationId": 42, "role": "assistant", "content": "The capital of France is Paris.", "createdAt": "2026-04-15T10:00:02Z" }
]
```

#### Create a conversation record manually

```
POST /api/v1/conversations
Content-Type: application/json
```

Useful for creating a labelled conversation before sending the first chat
message. The server records the client IP and User-Agent automatically.

#### Delete a conversation

```
DELETE /api/v1/conversations/{id}
```

Returns 204 on success, 404 if not found.

---

## 7. Web UI

A React-based web interface is bundled via [Quarkus Quinoa](https://quarkiverse.github.io/quarkiverse-docs/quarkus-quinoa/dev/).
It is served from the root URL and provides:

- **Inference UI** (`/infer`) — select a loaded SMILE model from the sidebar,
  fill in the auto-generated form (derived from the model schema), and view
  the prediction result.
- **Chat UI** (`/chat`) — a conversational interface for the Llama chat service
  with streaming token display and Markdown/math rendering.

In dev mode the React development server runs on port **5173** and requests
are proxied to the Quarkus backend. The production build (`dist/`) is served
statically by the Quarkus process.

---

## 8. Database

Chat conversation history requires a relational database.

| Profile | Backend | URL |
|---|---|---|
| Production | PostgreSQL | `jdbc:postgresql://localhost:5432/smile` |
| Dev | SQLite | `jdbc:sqlite:./smile_serve.db` |
| Test | H2 (in-memory) | `jdbc:h2:mem:test;DB_CLOSE_DELAY=-1` |

To enable the database in production set:

```properties
quarkus.hibernate-orm.active=true
quarkus.datasource.username=<user>
quarkus.datasource.password=<password>
```

Hibernate ORM uses `drop-and-create` by default. Change the strategy in
production to `update` or `validate`:

```properties
quarkus.hibernate-orm.schema-management.strategy=update
```

The database is **not required** for the ML or ONNX inference endpoints — only
for chat conversation persistence.

---

## 9. Testing

```shell
./gradlew :serve:test
```

The test profile (`%test.*`) configures the service with:

- An in-memory H2 database (no external database required).
- A pre-trained iris random forest model from
  `serve/src/test/resources/model/iris_random_forest.sml`.
- The ONNX model path also pointed at the test resources directory
  (no `.onnx` files present by default, so `OnnxService` starts empty).
- The chat model path set to a non-existent path so `ChatService` starts
  gracefully unavailable without attempting to load a GPU model.

The test class `InferenceResourceTest` covers:

| Test | Endpoint | Scenario |
|---|---|---|
| `testListModels` | `GET /models` | Returns the correct model IDs |
| `testGetModelMetadata` | `GET /models/{id}` | Returns algorithm, schema, and nullability |
| `testGetUnknownModelReturns404` | `GET /models/{id}` | 404 for unknown ID |
| `testPredictJsonReturnsPredictionAndProbabilities` | `POST /models/{id}` | Correct label + probabilities |
| `testPredictJsonWithZeroFeaturesReturnsValidPrediction` | `POST /models/{id}` | Edge case: all-zero features |
| `testPredictJsonMissingFieldReturns400` | `POST /models/{id}` | 400 for missing field |
| `testPredictUnknownModelReturns404` | `POST /models/{id}` | 404 for unknown model |
| `testStreamCsvReturnsPredictions` | `POST /models/{id}/stream` | 3 CSV rows → 3 SSE data lines |
| `testStreamJsonLinesReturnsPredictions` | `POST /models/{id}/stream` | 2 JSON-lines → 2 SSE data lines |
| `testStreamCsvTooFewColumnsEmitsNoPredictions` | `POST /models/{id}/stream` | Bad CSV closes stream |
| `testStreamUnknownModelReturns404` | `POST /models/{id}/stream` | 404 before stream starts |

---

## API Quick Reference

### Classic ML — `/api/v1/models`

| Method | Path | Description |
|---|---|---|
| `GET` | `/models` | List all loaded model IDs |
| `GET` | `/models/{id}` | Get model metadata and schema |
| `POST` | `/models/{id}` | Single JSON inference |
| `POST` | `/models/{id}/stream` | Streaming CSV or JSON-lines inference |

### ONNX — `/api/v1/onnx`

| Method | Path | Description |
|---|---|---|
| `GET` | `/onnx` | List all loaded ONNX model IDs |
| `GET` | `/onnx/{id}` | Get graph info, input/output shapes |
| `POST` | `/onnx/{id}` | Single JSON inference |
| `POST` | `/onnx/{id}/stream` | Streaming CSV or JSON-lines inference |

### Chat — `/api/v1/chat` and `/api/v1/conversations`

| Method | Path | Description |
|---|---|---|
| `POST` | `/chat/completions` | Streaming LLM chat completion (SSE) |
| `GET` | `/conversations` | List conversations (paginated) |
| `GET` | `/conversations/{id}` | Get conversation metadata |
| `POST` | `/conversations` | Create a conversation record |
| `DELETE` | `/conversations/{id}` | Delete a conversation |
| `GET` | `/conversations/{id}/items` | List message turns (paginated) |


---

*SMILE Serve is free software under the GNU General Public License v3. For commercial use enquiries contact smile.sales@outlook.com.*

