# SMILE Serve User Guide

<p align="center">
  <img src="https://github.com/haifengl/smile/blob/master/serve/llama.jpg" width="400"/>
</p>

SMILE Serve is a production-ready inference server built on [Quarkus](https://quarkus.io/)
that brings together three complementary inference capabilities on the JVM:

| Capability | API prefix | Description                                                    |
|---|---|----------------------------------------------------------------|
| **Classic ML** | `/api/v1/ml/models` | Serialized SMILE models (`.sml`) — classifiers and regressors |
| **ONNX Runtime** | `/api/v1/onnx` | Any model in the ONNX open format (`.onnx`)                    |
| **LLM Chat** | `/api/v1/chat`, `/api/v1/models` | OpenAI-compatible chat completions and model list/retrieve |

A React-based web UI is bundled and served from the same process.

---

## Table of Contents

1. [Quick Start with Docker](#1-quick-start-with-docker)
2. [LLM Decode Benchmarks](#2-llm-decode-benchmarks)
3. [Building and Running](#3-building-and-running)
   - [Dev Mode](#31-dev-mode)
   - [Packaging as a JAR](#32-packaging-as-a-jar)
   - [Uber-JAR](#33-uber-jar)
   - [Native Executable](#34-native-executable)
4. [Configuration Reference](#4-configuration-reference)
5. [Classic ML Inference API](#5-classic-ml-inference-api)
   - [Model Format](#51-model-format)
   - [Get Model Metadata](#52-get-model-metadata)
   - [Single Inference (JSON)](#53-single-inference-json)
   - [Streaming Inference (CSV / JSON-lines)](#54-streaming-inference-csv--json-lines)
   - [Model IDs](#55-model-ids)
6. [ONNX Inference API](#6-onnx-inference-api)
   - [Model Format](#61-model-format)
   - [Get ONNX Model Info](#62-get-onnx-model-info)
   - [Single Inference (JSON)](#63-single-inference-json)
   - [Streaming Inference](#64-streaming-inference)
   - [Tensor Types and Shape Resolution](#65-tensor-types-and-shape-resolution)
7. [LLM Chat API](#7-llm-chat-api)
   - [List models](#71-list-models)
   - [Retrieve model](#72-retrieve-model)
   - [Chat Completions](#73-chat-completions)
   - [Conversation History API](#74-conversation-history-api)
8. [Web UI](#8-web-ui)
9. [Database](#9-database)
10. [Testing](#10-testing)

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

## 2. LLM Decode Benchmarks

Steady-state continuous-batching decode on NVIDIA A100 with FlashInfer,
`smile.chat.max-batch-size=48`, `max_tokens: 512`, greedy sampling
(`temperature: 0`), and a uniform decode cohort
(`smile.chat.admit-coalesce-ms=50` for parallel load tests). **Step** is
engine decode-step throughput (`batch / step_ms`); **aggregate** is
`AggregateTokenThroughput` over a ~3 s window (includes streaming / logging
overhead).

### Llama 3.1 8B Instruct

| Checkpoint | Weight path | Step | Aggregate | Step time | Forward / sample |
|---|---|---|---|---|---|
| [meta-llama/Llama-3.1-8B-Instruct](https://huggingface.co/meta-llama/Llama-3.1-8B-Instruct) | BF16 dense | **2087 tok/s** | ~2036 tok/s | 23 ms | 22 / 1 ms |
| [hugging-quants/Meta-Llama-3.1-8B-Instruct-AWQ-INT4](https://huggingface.co/hugging-quants/Meta-Llama-3.1-8B-Instruct-AWQ-INT4) | AWQ INT4 (Marlin) | **2667 tok/s** | ~2543 tok/s | 18 ms | 17 / 0 ms |

Both runs used a full batch of 48 active requests at locked positions
(e.g. `positions=[417..417]`). AWQ is ~28% faster at the same concurrency;
lighter weights also leave more free KV slots for longer context.

### Qwen3.8 27B

| Checkpoint | Setup | Step | Aggregate | Step time | Forward / sample |
|---|---|---|---|---|---|
| [Qwen/Qwen3.8-27B](https://huggingface.co/Qwen/Qwen3.8-27B) | BF16, TP=4 | **906 tok/s** | ~896 tok/s | 53 ms | 51 / 1 ms |

Hybrid Qwen3.5 stack (Gated DeltaNet + full attention) at the same concurrency
and locked positions (e.g. `positions=[466..466]`). Absolute throughput is
lower than 8B Llama as expected for a ~27B hybrid model across four GPUs;
per-request generation stays ~17 tok/s with the batch saturated. Qwen AWQ via
`compressed-tensors` / Marlin is not supported yet (detector currently treats
cards such as `cyankiwi/Qwen3.8-27B-AWQ-INT4` as dense).

---

## 3. Building and Running

All commands use the Gradle wrapper from the project root.

### 3.1 Dev Mode

Live-reload development mode — changes to Java sources are reflected without
restarting. The Quarkus Dev UI is available at <http://localhost:8888/q/dev/>.

```shell
./gradlew :serve:quarkusDev \
  --jvm-args="--add-opens java.base/java.lang=ALL-UNNAMED"
```

> The `--add-opens` flags are required by ONNX Runtime's Foreign Function Interface.
> The dev-mode HTTP port defaults to **8888** (configured via `%dev.quarkus.http.port`).

### 3.2 Packaging as a JAR

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

### 3.3 Uber-JAR

A single self-contained JAR (slower to start, simpler to deploy):

```shell
./gradlew :serve:build -Dquarkus.package.jar.type=uber-jar
java \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  --add-opens java.base/java.nio=ALL-UNNAMED \
  --enable-native-access ALL-UNNAMED \
  -jar build/smile-serve-runner.jar
```

### 3.4 Native Executable

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

## 4. Configuration Reference

Configuration is managed in `src/main/resources/application.properties`.
Quarkus profile prefixes (`%dev.`, `%test.`) override the base values in
the corresponding profiles.

| Property | Default | Description |
|---|---|---|
| `quarkus.http.port` | `8080` | HTTP listen port (`%dev` default: `8888`) |
| `quarkus.rest.path` | `/api/v1` | Global REST path prefix |
| `smile.serve.model` | `../model` | Path to a `.sml` file or directory of `.sml` files |
| `smile.onnx.model` | `../model` | Path to a `.onnx` file or directory of `.onnx` files |
| `smile.chat.model` | `../model/Llama3.1-8B-Instruct` | Local HF-layout checkpoint directory, or Hugging Face repo id (`owner/name`). Tokenizer is resolved next to the checkpoint (`original/tokenizer.model`, `tokenizer.model`, or HF `tokenizer.json` for AWQ/GPTQ cards) |
| `smile.chat.max-seq-len` | `0` (auto) | Max context (prompt+output), like vLLM `--max-model-len` / SGLang `--context-length`. `<=0` uses `max_position_embeddings` from the model config; set explicitly (e.g. `8192`) to cap large-window models such as Qwen3.5 |
| `smile.chat.max-batch-size` | `1` | Max in-flight chat generations (`InferenceEngine` Fluid Injection cap). Values `>1` enable continuous batching. A waiting request is admitted only when free KV can reserve its full `prompt + max_tokens` window (capped by `max-seq-len`); otherwise it stays queued. |
| `smile.chat.max-decode-batch` | `0` (same as max-batch-size) | Cap on requests per GPU `decodeStep`; `0` means use `max-batch-size` |
| `smile.chat.prefill-token-budget` | `2048` | Max prompt tokens prefilled per scheduler tick (chunked prefill so long prompts do not stall decode) |
| `smile.chat.admission-timeout-ms` | `120000` | Max ms a job may wait for KV admission before failing (`0` = wait indefinitely) |
| `smile.chat.admit-coalesce-ms` | `0` | When idle, delay first admission up to this many ms so a parallel burst can fill the queue; stops early once `queued >= max-batch-size`. `0` disables (default). Use e.g. `50` for `curl --parallel` load tests |
| `smile.chat.admission-timeout-ms` | `120000` | Fail a waiting job if KV cannot admit it within this many ms (`0` = wait until Instant Eviction frees capacity) |
| `smile.chat.mem-fraction-static` | `0.85` | SGLang `--mem-fraction-static`: fraction `y` of **total** GPU memory for the static region (weights + DeltaNet + KV). Leaves ~(1−y)×total free for activations (plus a small soft margin). Idle use near `y×total` is expected. Short-prompt OOM usually means activation peak/leak during forward — try `0.75` and/or a lower `max-seq-len` on 40GB TP=2 Qwen desktops. Pool is static (no per-request growth); when free KV is exhausted, generation stops early with partial output (`finish_reason=length`) |
| `smile.chat.model-loader-threads` | `0` (auto) | Concurrent safetensors shard readers. Each worker loads one shard to CPU then fans out to TP ranks. Peak host RAM ≈ `threads × shard size`. `0` = `min(8, CPUs)`, capped by number of shard files. Use `1`–`2` on RAM-tight desktops |
| `smile.chat.devices` | `0` | CUDA device index, or comma-separated TP list (`0,7`). `%dev` default: `7` |
| `smile.chat.tensor-parallel-size` | `1` | TP world size; with a single `devices` entry expands to consecutive GPUs |
| `smile.chat.pipeline-parallel-size` | `1` | Must stay `1` until multi-node PP |
| `smile.chat.kv-cache.dtype` | _(unset)_ | KV-cache element dtype (`bfloat16`, `float16`, `float32`, `fp8_e4m3`, `fp8_e5m2`, …). When unset, uses `torch_dtype` from the model `config.json`. Set `fp8_e4m3` for ~2× KV capacity (longer context); attention dequants to BF16/FP16. Independent of weight quant. |
| `smile.chat.quantization` | `auto` | Weight GEMM: `auto` / `dense` / `fp8` / `nvfp4` / `marlin`. `auto` selects FP8/NVFP4 on Hopper/Blackwell for native checkpoints, Marlin on Ampere/Ada for GPTQ/AWQ. GPTQ/AWQ on Hopper+ fails fast (no silent Marlin). **Qwen hybrid:** native FP8 (e.g. Qwen3.8-FP8) on H100+ (`sm_90+`) uses block-128 `weight_scale_inv` via LibTorch `_scaled_mm_v2` (`Fp8BlockLinear`); rebuild `libsmile_torch` after pulling. Vision tower and `lm_head` stay BF16. A100 still fails fast on native FP8 Qwen. GPTQ/AWQ/Marlin for Qwen remain unsupported. Marlin AWQ/GPTQ install batch-reads each safetensors shard once and packs linears in parallel (direct int4→Marlin, no FP16 round-trip); expect on the order of tens of seconds for 8B AWQ on A100 rather than multi-minute phase-1 loads. GPTQ act-order (`g_idx`) is rejected. |
| `smile.chat.kv-cache.page-size` | `16` | Tokens per radix / KV pool page (prefix match and insert are page-aligned) |
| `smile.chat.kv-cache.prefix-reuse` | `true` | Match/insert prompts in the radix KV tree (SGLang-style). Hybrid Qwen also needs `hybrid-prefix-replay` |
| `smile.chat.kv-cache.hybrid-prefix-replay` | `true` | On a hybrid Qwen prefix hit, replay the matched prefix to restore DeltaNet state while sharing KV pages. Set `false` to force-disable hybrid prefix reuse |
| `quarkus.datasource.db-kind` | `postgresql` | Database backend for chat history |
| `quarkus.datasource.jdbc.url` | `jdbc:postgresql://localhost:5432/smile` | JDBC connection URL |
| `quarkus.hibernate-orm.active` | `false` | Enable ORM (set `true` when database is available) |

**Override at runtime** with `-D` system properties, for example:

```shell
java ... -Dsmile.serve.model=/data/models/rf_classifier.sml -jar quarkus-run.jar
```

---

## 5. Classic ML Inference API

### 5.1 Model Format

Classic ML models are serialized Java objects saved in `.sml` files by the
SMILE `smile.model.Model` framework. They carry:

- The trained algorithm (random forest, SVM, gradient boost, etc.)
- The input feature schema (field names and data types)
- Training / validation metrics
- Optional metadata tags (`id`, `version`, user-defined properties)

At startup, `InferenceService` scans the path specified by the property
`smile.serve.model`. If the path is a regular `.sml` file only that model
is loaded; if it is a directory every `.sml` file in the directory is loaded.

### 5.2 Get Model Metadata

Returns the algorithm name, input schema, and tags for a model.
Use `GET /api/v1/models` to discover loaded model IDs.

```
GET /api/v1/ml/models/{id}
```

**Example:**

```shell
curl http://localhost:8080/api/v1/ml/models/iris_random_forest-1
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

### 5.3 Single Inference (JSON)

Send one sample as a JSON object and receive the prediction synchronously.

```
POST /api/v1/ml/models/{id}
Content-Type: application/json
```

The request body is a flat JSON object whose keys are the feature names
defined in the model schema. **All non-nullable fields are required.**

**Classification example (iris):**

```shell
curl -X POST http://localhost:8080/api/v1/ml/models/iris_random_forest-1 \
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

### 5.4 Streaming Inference (CSV / JSON-lines)

Process many samples in a single request. The server returns results as a
[Server-Sent Events](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events)
stream — one `data:` line per input sample.

```
POST /api/v1/ml/models/{id}/stream
Content-Type: text/plain          ← CSV mode
Content-Type: application/json   ← JSON-lines mode
```

#### CSV mode (`text/plain`)

Each non-blank line is a comma-separated row of feature values in the **same
column order as the model schema** (alphabetical by field name, as shown by
`GET /api/v1/ml/models/{id}`).

```shell
cat iris.csv | curl -X POST \
  -H "Content-Type: text/plain" \
  --data-binary @- \
  http://localhost:8080/api/v1/ml/models/iris_random_forest-1/stream
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
  http://localhost:8080/api/v1/ml/models/iris_random_forest-1/stream
```

Where `iris.jsonl` contains:

```json
{"sepallength":5.1,"sepalwidth":3.5,"petallength":1.4,"petalwidth":0.2}
{"sepallength":6.7,"sepalwidth":3.0,"petallength":5.2,"petalwidth":2.3}
```

### 5.5 Model IDs

A model's ID is constructed as `<name>-<version>` from the model's embedded
metadata tags (`smile.model.Model.ID` and `smile.model.Model.VERSION`).
If those tags are absent, the file name stem is used as the name and `"1"` as
the version. For example, a file named `iris_random_forest.sml` with no ID
tag gets the ID `iris_random_forest-1`.

---

## 6. ONNX Inference API

The ONNX endpoint exposes any model in the
[ONNX open format](https://onnx.ai/) through SMILE's native ONNX Runtime
binding (`smile.onnx`). This covers models exported from PyTorch, TensorFlow,
scikit-learn (via `sklearn-onnx`), and many other frameworks.

### 6.1 Model Format

At startup, `OnnxService` scans the folder specified by the property
`smile.onnx.model`. Every `.onnx` file found is loaded into an
`InferenceSession`. The model ID is the file name without
the `.onnx` extension (e.g., `resnet50.onnx` → ID `resnet50`).

**Native library:** the ONNX Runtime shared library
(`libonnxruntime.so` / `libonnxruntime.dylib` / `onnxruntime.dll`) must be
on the OS library search path, and the JVM must
include `--enable-native-access=ALL-UNNAMED`. If the library is missing,
startup continues without ONNX models (endpoints return 404) rather than
aborting the whole serve process. Download pre-built binaries from the
[ORT releases](https://github.com/microsoft/onnxruntime/releases) page.

### 6.2 Get ONNX Model Info

Returns graph metadata and the typed, shaped input/output node descriptors.
Use `GET /api/v1/models` to discover loaded model IDs.

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

### 6.3 Single Inference (JSON)

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

The Infer UI image path resizes to the model’s H×W, scales to `[0, 1]`, then
applies ImageNet channel normalization `(x - mean) / std` with
`mean=[0.485, 0.456, 0.406]`, `std=[0.229, 0.224, 0.225]` (NCHW or NHWC as
declared). When posting tensors yourself, use the same layout and
normalization the model was trained with.

Response — a JSON object mapping each **output name** to a flat array of
**raw logits** (not probabilities). ImageNet classifiers from the ONNX Model
Zoo typically omit a Softmax node; apply `MathEx.softmax` before interpreting
scores as class probabilities (same as `InferenceSessionTest` in `core`).

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

### 6.4 Streaming Inference

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

### 6.5 Tensor Types and Shape Resolution

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

## 7. LLM Chat API

SMILE Serve includes a Java implementation of
[Llama 3](https://github.com/haifengl/smile/tree/master/deep/src/main/java/smile/llm/llama)
for on-premise LLM inference. The chat API is designed to be compatible with
the OpenAI Chat Completions interface.

The LLM is optional: if the path specified by the property `smile.chat.model`
does not exist on the file system, `ChatService` starts in an *unavailable*
state and every request to the chat endpoints returns **HTTP 503 Service Unavailable**.

### 7.1 List models

```
GET /api/v1/models
```

OpenAI-compatible catalog of **all** loaded models — chat LLMs, ONNX graphs,
and SMILE {@code .sml} models
([List models](https://developers.openai.com/api/reference/resources/models/methods/list)).

Inference still uses type-specific paths:
`/api/v1/chat/completions`, `/api/v1/onnx/{id}`, `/api/v1/ml/models/{id}`.

```shell
curl http://localhost:8080/api/v1/models
```

```json
{
  "object": "list",
  "data": [
    {
      "id": "meta-llama/Llama-3.1-8B-Instruct",
      "object": "model",
      "created": 1741900000,
      "owned_by": "meta-llama",
      "shutdown_date": null,
      "kind": "LLM"
    },
    {
      "id": "iris_random_forest-1",
      "object": "model",
      "created": 1710000000,
      "owned_by": "Unknown",
      "shutdown_date": null,
      "kind": "random-forest"
    },
    {
      "id": "resnet50",
      "object": "model",
      "created": 1710000000,
      "owned_by": "Unknown",
      "shutdown_date": null,
      "kind": "ONNX"
    }
  ]
}
```

`kind` values:

- **`LLM`** — chat / completion models
- **`ONNX`** — ONNX Runtime graphs
- **SMILE algorithm name** — e.g. `random-forest`, `cart`, `logistic` (from the `.sml` model)

`owned_by` rules:

- **Chat (HF):** first segment of the repo id (`meta-llama/...` → `meta-llama`)
- **Chat (local):** first segment of `Llama.family()` (`meta/llama3` → `meta`)
- **SMILE `.sml`:** tag `author`, else `owner`; otherwise `Unknown`
- **ONNX:** custom metadata `author`/`owner` when present; otherwise `Unknown`

### 7.2 Retrieve model

```
GET /api/v1/models/{id}
```

OpenAI-compatible
[retrieve model](https://developers.openai.com/api/reference/resources/models/methods/retrieve).
Returns the same base `ModelObject` fields as list entries, plus an optional
type-specific detail block. Does **not** run inference — use
`/chat/completions`, `/onnx/{id}`, or `/ml/models/{id}` for that.

Ids may contain slashes (e.g. Hugging Face repo ids).

| `kind` | Extra field | Contents |
|---|---|---|
| SMILE algorithm | `smile` | `formula`, `schema`, `tags`, `train` / `validation` / `test` metrics (finite values only) |
| `ONNX` | `onnx` | producer, domain, graph info, I/O shapes, custom metadata from the `.onnx` file |
| `LLM` | `llm` | family, source (`local`/`huggingface`), architecture from `config.json` / `params.json` |

List responses omit `smile` / `onnx` / `llm` so the catalog stays lean.

```shell
curl http://localhost:8080/api/v1/models/iris_random_forest-1
```

```json
{
  "id": "iris_random_forest-1",
  "object": "model",
  "created": 1710000000,
  "owned_by": "Unknown",
  "shutdown_date": null,
  "kind": "random-forest",
  "smile": {
    "formula": "class ~ .",
    "schema": {
      "petallength": { "type": "float", "nullable": false }
    },
    "tags": {},
    "train": {
      "accuracy": 0.97,
      "size": 150
    },
    "validation": null,
    "test": null
  }
}
```

### 7.3 Chat Completions

```
POST /api/v1/chat/completions
Content-Type: application/json
```

Tokens are streamed back as Server-Sent Events when `stream` is true,
or returned as a single OpenAI `chat.completion` JSON object when `stream`
is false or omitted (OpenAI default). The conversation (user message +
assistant reply) is automatically persisted to the configured database after
generation finishes.

Serve runs an {@code InferenceEngine} continuous-batching loop (Fluid Injection
admission, chunked prefill, batched decode, Instant Eviction on abort). Offline
library callers may still use {@code LanguageModel.generate}; HTTP chat always
goes through the engine. Client disconnect on an SSE stream aborts the
{@code GenerationHandle}, which Instant-Evicts that request's KV pages without
stopping peer in-flight generations.

**Request body fields (`snake_case`):**

| Field | Type | Default | Description |
|---|---|---|---|
| `model` | `string` | loaded model | Must match the loaded model id when set (HF repo id or local directory name); omit/empty to use the loaded model |
| `messages` | `Message[]` | *required* | Ordered dialog turns |
| `conversation` | `string` | `null` | Existing conversation id (`conv_<n>`) to append to |
| `max_tokens` | `int` | remaining context | Max new tokens (legacy OpenAI name). Default: `max-model-len − prompt_len` |
| `max_completion_tokens` | `int` | — | Alias for `max_tokens`; takes precedence when set |
| `temperature` | `double` | `0.6` | Sampling temperature (higher = more random) |
| `top_p` | `double` | `0.9` | Nucleus-sampling threshold |
| `logprobs` | `boolean` | `false` | Include log-probabilities |
| `seed` | `long` | `0` | Random seed (0 = non-deterministic) |
| `stream` | `boolean` | `false` | `true` → SSE chunks; `false`/omitted → single `chat.completion` JSON |
| `tools` | `array` | — | OpenAI tool definitions (`type: function`) for Qwen3 / Qwen3.5 / Qwen3.8 |
| `tool_choice` | `string\|object` | `auto` | `auto` / `none` / `required` / `{"type":"function","function":{"name":"…"}}` |
| `parallel_tool_calls` | `boolean` | `true` | Allow multiple tool calls in one assistant turn |

Each `Message` has a `role` (`system`, `user`, `assistant`, or `tool`) and `content`.
Assistant turns may include `tool_calls`; tool-result turns use `role: "tool"` and `tool_call_id`.
Legacy `"ipython"` is accepted as an alias for `"tool"`.

#### Tool calling (Qwen3 / Qwen3.5 / Qwen3.8)

OpenAI-compatible tool calling is supported for latest Qwen checkpoints (XML
`<tool_call>` format). Clients run tools locally and continue the dialog:

```shell
curl -X POST http://localhost:8080/api/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [{"role": "user", "content": "What is the weather in SF?"}],
    "tools": [{
      "type": "function",
      "function": {
        "name": "get_current_temperature",
        "description": "Get temperature for a location",
        "parameters": {
          "type": "object",
          "properties": {"location": {"type": "string"}},
          "required": ["location"]
        }
      }
    }],
    "tool_choice": "auto"
  }'
```

A successful tool-call response uses `finish_reason: "tool_calls"` and
`message.tool_calls[]`. Append tool results as `{"role":"tool","tool_call_id":"…","content":"…"}`
and call again for the final answer. Streaming (v1) buffers the completion and
replays `delta.tool_calls` at the end (not token-true XML streaming).

Smoke test with the OpenAI Python SDK:

```shell
python serve/scripts/test_tool_calling.py --base-url http://localhost:8888/api/v1
```

**Streaming example (`stream: true`):**

```shell
curl -X POST http://localhost:8080/api/v1/chat/completions \
  -H "Content-Type: application/json" \
  -N \
  -d '{
    "stream": true,
    "messages": [
      {"role": "system",  "content": "You are a helpful assistant."},
      {"role": "user",    "content": "What is the capital of France?"}
    ],
    "max_tokens": 256,
    "temperature": 0.7
  }'
```

The response is an SSE stream of OpenAI-shaped `chat.completion.chunk` JSON
events, terminated by `data: [DONE]`.

**Non-streaming example (`stream` omitted or `false`):**

```shell
curl -X POST http://localhost:8080/api/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [
      {"role": "user", "content": "What is the capital of France?"}
    ],
    "max_completion_tokens": 256
  }'
```

```json
{
  "id": "chatcmpl-...",
  "object": "chat.completion",
  "created": 1741900000,
  "model": "meta-llama/Llama-3.1-8B-Instruct",
  "choices": [
    {
      "index": 0,
      "message": { "role": "assistant", "content": "Paris." },
      "logprobs": null,
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 20,
    "completion_tokens": 3,
    "total_tokens": 23
  }
}
```

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

### 7.4 Conversation History API

Chat history is stored in a relational database (PostgreSQL in production,
H2 in dev mode). The API base path is `/api/v1/conversations`.

Conversations are **scoped to authenticated users**. Guests (no session) may
still create conversations and chat, but those rows are tied to client IP and
do not appear in the sidebar list until the user signs in (recent guest threads
from the same IP within 24 hours are merged on login).

On **`localhost` or `127.0.0.1`** (any port), the server auto-logs you in as
the local **`me`** account so dev workflows work without OAuth.

#### Authentication

| Endpoint | Description |
|---|---|
| `GET /api/v1/auth/me` | Current user (`logged_in`, `user` profile) or guest |
| `GET /api/v1/auth/login/google` | Redirect to Google OAuth (requires config below) |
| `GET /api/v1/auth/callback/google` | OAuth callback (sets session cookie) |
| `POST /api/v1/auth/logout` | Clear session |
| `GET /api/v1/users/me` | Profile (authenticated) |
| `PATCH /api/v1/users/me` | Update display name, avatar URL, personal instructions |

Configure Google login in `application.properties` (or env):

```properties
smile.auth.google.client-id=${GOOGLE_CLIENT_ID}
smile.auth.google.client-secret=${GOOGLE_CLIENT_SECRET}
# Optional; default is {origin}/api/v1/auth/callback/google
# smile.auth.google.redirect-uri=https://your-host/api/v1/auth/callback/google
smile.auth.session-secret=${SMILE_SESSION_SECRET}
```

Register the redirect URI in Google Cloud Console. **Personal instructions**
from the user profile are injected as the chat system prompt when logged in.

Create, retrieve, update, and delete follow the
[OpenAI Conversations](https://developers.openai.com/api/reference/resources/conversations)
shapes. Conversation ids are strings of the form `conv_<n>`. List, `PATCH`
(title/pin), and `GET .../items` are smile extensions.

#### List conversations (smile extension, **requires login**)

```
GET /api/v1/conversations?pageIndex=0&pageSize=25&q=search&pinned=true
```

Returns the signed-in user's conversations (title, pinned, updated_at).
Search matches **title and message content**.

```shell
curl "http://localhost:8080/api/v1/conversations?pageSize=10"
```

#### Create a conversation

```
POST /api/v1/conversations
Content-Type: application/json
```

Optional body fields: `metadata` (≤16 string pairs) and `items` (≤20 message
items with `role` + text `content`).

```shell
curl http://localhost:8080/api/v1/conversations \
  -H "Content-Type: application/json" \
  -d '{"metadata":{"topic":"demo"},"items":[{"type":"message","role":"user","content":"Hello!"}]}'
```

```json
{
  "id": "conv_1",
  "object": "conversation",
  "created_at": 1741900000,
  "metadata": {"topic": "demo"}
}
```

#### Retrieve a conversation

```
GET /api/v1/conversations/{conversation_id}
```

#### Update a conversation

```
POST /api/v1/conversations/{conversation_id}
Content-Type: application/json
```

```shell
curl http://localhost:8080/api/v1/conversations/conv_1 \
  -H "Content-Type: application/json" \
  -d '{"metadata":{"topic":"project-x"}}'
```

#### Patch sidebar fields (smile extension, **requires ownership**)

```
PATCH /api/v1/conversations/{conversation_id}
Content-Type: application/json

{"title": "My chat", "pinned": true}
```

#### Delete a conversation

```
DELETE /api/v1/conversations/{conversation_id}
```

```json
{
  "id": "conv_1",
  "object": "conversation.deleted",
  "deleted": true
}
```

#### Get conversation messages (smile extension)

```
GET /api/v1/conversations/{conversation_id}/items?pageIndex=0&pageSize=25
```

Returns the individual message turns (`role` + `content` + `createdAt`)
in chronological order.

```shell
curl http://localhost:8080/api/v1/conversations/conv_42/items
```

```json
[
  { "id": 1, "conversationId": 42, "role": "user",      "content": "What is the capital of France?", "createdAt": "2026-04-15T10:00:00Z" },
  { "id": 2, "conversationId": 42, "role": "assistant", "content": "The capital of France is Paris.", "createdAt": "2026-04-15T10:00:02Z" }
]
```

---

## 8. Web UI

A React-based web interface is bundled via [Quarkus Quinoa](https://quarkiverse.github.io/quarkiverse-docs/quarkus-quinoa/dev/).
It is served from the root URL and provides:

- **Inference UI** (`/infer`) — unified model shell: **collapsible** sidebar lists
  **chat**, **SMILE** (`.sml`), and **ONNX** (`.onnx`) models. Selecting a chat
  model embeds the chat module with a **collapsible right nav** (history, login,
  settings). SMILE models get a schema-driven form; ONNX models get a numeric
  form from tensor shapes, or an image upload when a 4-D vision-like input is
  detected (overrideable).
- **Chat UI** (`/chat`) — standalone chat with the same **right nav panel**
  (New Chat always available; history/login/settings when signed in). Streaming
  tokens, Markdown/math. On localhost, you are auto-signed-in as **`me`**.

Full UI/auth design: [docs/chat-sidebar-plan.md](docs/chat-sidebar-plan.md).

In dev mode the React development server runs on port **5173** and requests
are proxied to the Quarkus backend. The production build (`dist/`) is served
statically by the Quarkus process.

---

## 9. Database

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

## 10. Testing

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
| `testGetModelMetadata` | `GET /ml/models/{id}` | Returns algorithm, schema, and nullability |
| `testGetUnknownModelReturns404` | `GET /ml/models/{id}` | 404 for unknown ID |
| `testPredictJsonReturnsPredictionAndProbabilities` | `POST /ml/models/{id}` | Correct label + probabilities |
| `testPredictJsonWithZeroFeaturesReturnsValidPrediction` | `POST /ml/models/{id}` | Edge case: all-zero features |
| `testPredictJsonMissingFieldReturns400` | `POST /ml/models/{id}` | 400 for missing field |
| `testPredictUnknownModelReturns404` | `POST /ml/models/{id}` | 404 for unknown model |
| `testStreamCsvReturnsPredictions` | `POST /ml/models/{id}/stream` | 3 CSV rows → 3 SSE data lines |
| `testStreamJsonLinesReturnsPredictions` | `POST /ml/models/{id}/stream` | 2 JSON-lines → 2 SSE data lines |
| `testStreamCsvTooFewColumnsEmitsNoPredictions` | `POST /ml/models/{id}/stream` | Bad CSV closes stream |
| `testStreamUnknownModelReturns404` | `POST /ml/models/{id}/stream` | 404 before stream starts |

---

## API Quick Reference

### Classic ML — `/api/v1/ml/models`

| Method | Path | Description |
|---|---|---|
| `GET` | `/ml/models/{id}` | Get model metadata and schema |
| `POST` | `/ml/models/{id}` | Single JSON inference |
| `POST` | `/ml/models/{id}/stream` | Streaming CSV or JSON-lines inference |

### ONNX — `/api/v1/onnx`

| Method | Path | Description |
|---|---|---|
| `GET` | `/onnx/{id}` | Get graph info, input/output shapes |
| `POST` | `/onnx/{id}` | Single JSON inference |
| `POST` | `/onnx/{id}/stream` | Streaming CSV or JSON-lines inference |

### Chat — `/api/v1/models`, `/api/v1/chat`, `/api/v1/conversations`

| Method | Path | Description |
|---|---|---|
| `GET` | `/models` | List all loaded models — chat, ONNX, SMILE (OpenAI-compatible) |
| `GET` | `/models/{id}` | Retrieve a model by id (OpenAI-compatible) |
| `POST` | `/chat/completions` | Chat completion — SSE when `stream: true`, JSON when `stream: false` |
| `GET` | `/conversations` | List conversations (paginated; smile extension) |
| `GET` | `/conversations/{conversation_id}` | Retrieve conversation (OpenAI-compatible) |
| `POST` | `/conversations` | Create conversation (OpenAI-compatible) |
| `POST` | `/conversations/{conversation_id}` | Update conversation metadata (OpenAI-compatible) |
| `DELETE` | `/conversations/{conversation_id}` | Delete conversation (OpenAI-compatible) |
| `GET` | `/conversations/{conversation_id}/items` | List message turns (paginated; smile extension) |


---

*SMILE Serve is free software under the GNU General Public License v3. For commercial use enquiries contact smile.sales@outlook.com.*

