# SMILE — ONNX User Guide & Tutorial

The module `smile.onnx` provides an idiomatic **Java API for running ONNX models** with the
[ONNX Runtime](https://onnxruntime.ai/) inference engine.

---

## Table of Contents

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Package Structure](#package-structure)
4. [Quick Start](#quick-start)
5. [Core Concepts](#core-concepts)
   - [InferenceSession](#inferencesession)
   - [OrtValue — Tensors](#ortvalue--tensors)
   - [SessionOptions](#sessionoptions)
   - [RunOptions](#runoptions)
   - [Environment](#environment)
6. [Model Introspection](#model-introspection)
   - [NodeInfo & TensorInfo](#nodeinfo--tensorinfo)
   - [ModelMetadata](#modelmetadata)
7. [Data Types](#data-types)
   - [ElementType](#elementtype)
   - [OnnxType](#onnxtype)
8. [Execution Providers (GPU Acceleration)](#execution-providers-gpu-acceleration)
9. [Error Handling](#error-handling)
10. [Resource Management](#resource-management)
11. [Logging](#logging)
12. [Performance Tuning](#performance-tuning)
13. [Tutorials](#tutorials)
    - [Tutorial 1: Image Classification with ResNet-50](#tutorial-1-image-classification-with-resnet-50)
    - [Tutorial 2: NLP with a BERT Tokenizer Model](#tutorial-2-nlp-with-a-bert-tokenizer-model)
    - [Tutorial 3: Sharing an Environment across Sessions](#tutorial-3-sharing-an-environment-across-sessions)
    - [Tutorial 4: GPU Inference with CUDA](#tutorial-4-gpu-inference-with-cuda)
    - [Tutorial 5: Loading a Model from a JAR Resource](#tutorial-5-loading-a-model-from-a-jar-resource)
    - [Tutorial 6: Profiling an Inference Session](#tutorial-6-profiling-an-inference-session)
    - [Tutorial 7: Cancelling a Long-Running Inference](#tutorial-7-cancelling-a-long-running-inference)
14. [API Quick Reference](#api-quick-reference)

---

## Overview

The `smile.onnx` package wraps the ONNX Runtime C API through the Panama FFM
layer exposed by `smile.onnx.foreign`.  You never need to interact with that
low-level layer directly; every operation is available through the high-level
Java classes described in this guide.

```
┌──────────────────────────────────────┐
│           Your Application           │
├──────────────────────────────────────┤
│          smile.onnx (this guide)     │
│  InferenceSession  OrtValue  ...     │
├──────────────────────────────────────┤
│   smile.onnx.foreign  (Panama FFM)   │
│      OrtApi  OrtApiBase  ...         │
├──────────────────────────────────────┤
│    onnxruntime shared library (.so   │
│    / .dylib / .dll)                  │
└──────────────────────────────────────┘
```

---

## Prerequisites

| Requirement | Minimum version |
|---|---|
| Java | 22 (Panama FFM stable) |
| ONNX Runtime native library | 1.18+ (API version 22) |
| JVM flag | `--enable-native-access=ALL-UNNAMED` |

### Installing the ONNX Runtime native library

Download the pre-built packages for your platform from the
[ORT releases page](https://github.com/microsoft/onnxruntime/releases) and
place the shared library (`libonnxruntime.so`, `libonnxruntime.dylib`, or
`onnxruntime.dll`) on the OS library search path:

```bash
# Linux
export LD_LIBRARY_PATH=/path/to/onnxruntime/lib:$LD_LIBRARY_PATH

# macOS
export DYLD_LIBRARY_PATH=/path/to/onnxruntime/lib:$DYLD_LIBRARY_PATH

# Windows (PowerShell)
$env:PATH = "C:\path\to\onnxruntime\lib;" + $env:PATH
```

### JVM flag

Add this flag to every `java` invocation that uses `smile.onnx`:

```
--enable-native-access=ALL-UNNAMED
```

For example:

```bash
java --enable-native-access=ALL-UNNAMED -jar myapp.jar
```

Or in `build.gradle.kts`:

```kotlin
tasks.named<Test>("test") {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
```

---

## Package Structure

| Class / Enum | Role |
|---|---|
| `InferenceSession` | Loads a model and runs inference |
| `OrtValue` | Container for tensor data (input and output) |
| `SessionOptions` | Configures a session before loading |
| `RunOptions` | Configures a single inference run |
| `Environment` | Shared OrtEnv (thread pools, logging) |
| `ModelMetadata` | Producer, version, custom metadata record |
| `NodeInfo` | Name + type information for one input/output node |
| `TensorInfo` | Element type + shape for a tensor node |
| `ElementType` | Enum of ONNX tensor element data types |
| `OnnxType` | Enum of ONNX value kinds (TENSOR, SEQUENCE, MAP…) |
| `GraphOptimizationLevel` | Enum of graph optimization levels |
| `ExecutionMode` | Enum of sequential / parallel execution modes |
| `LoggingLevel` | Enum of ORT logging severity levels |
| `OnnxException` | Runtime exception thrown on ORT errors |

---

## Quick Start

```java
import smile.onnx.*;
import java.util.Map;

// 1. Load the model
try (var session = InferenceSession.create("resnet50.onnx")) {

    // 2. Inspect inputs
    session.inputNames().forEach(System.out::println);
    // → "data"

    // 3. Build an input tensor  (batch=1, channels=3, height=224, width=224)
    float[] pixels = preprocessImage(...);   // your image preprocessing
    long[]  shape  = { 1, 3, 224, 224 };

    try (OrtValue input = OrtValue.fromFloatArray(pixels, shape)) {

        // 4. Run inference
        OrtValue[] outputs = session.run(Map.of("data", input));

        // 5. Read the result
        float[] scores = outputs[0].toFloatArray();
        int classId = argmax(scores);
        System.out.println("Predicted class: " + classId);

        // 6. Release output tensors
        for (OrtValue v : outputs) v.close();
    }
}
```

---

## Core Concepts

### InferenceSession

`InferenceSession` is the central object.  It loads an ONNX model, optimizes
its graph, and executes inference.

#### Creating a session

```java
// From a file path (default options)
InferenceSession session = InferenceSession.create("model.onnx");

// From a file path with custom options
try (var opts = new SessionOptions()) {
    opts.setIntraOpNumThreads(4)
        .setGraphOptimizationLevel(GraphOptimizationLevel.ENABLE_ALL);
    InferenceSession session = InferenceSession.create("model.onnx", opts);
}

// From a byte array (e.g. loaded from a database or JAR resource)
byte[] modelBytes = Files.readAllBytes(Path.of("model.onnx"));
InferenceSession session = InferenceSession.create(modelBytes);

// From a byte array with custom options
InferenceSession session = InferenceSession.create(modelBytes, opts);
```

`InferenceSession` implements `AutoCloseable`; always use try-with-resources
or call `close()` explicitly to free the native session.

#### Running inference

```java
// Run all outputs (most common)
OrtValue[] outputs = session.run(inputs);

// Run a selected subset of outputs
OrtValue[] outputs = session.run(inputs, new String[]{ "output_0", "output_1" });

// Run with per-call options
try (var runOpts = new RunOptions()) {
    runOpts.setLogTag("my-run");
    OrtValue[] outputs = session.run(inputs, outputNames, runOpts);
}
```

`inputs` is a `Map<String, OrtValue>` mapping each input name to its value.
The `run` method is thread-safe; multiple threads may call it concurrently on
the same session.

---

### OrtValue — Tensors

`OrtValue` wraps an ORT tensor and provides Java-friendly factory and
extraction methods.  It implements `AutoCloseable`.

#### Creating tensors from Java arrays

```java
// float  (FLOAT / fp32)
float[] data  = { 1f, 2f, 3f, 4f };
long[]  shape = { 2, 2 };
OrtValue v = OrtValue.fromFloatArray(data, shape);

// double (DOUBLE / fp64)
OrtValue v = OrtValue.fromDoubleArray(new double[]{ 1.0, 2.0 }, new long[]{ 2 });

// int32
OrtValue v = OrtValue.fromIntArray(new int[]{ 10, 20 }, new long[]{ 2 });

// int64
OrtValue v = OrtValue.fromLongArray(new long[]{ 100L, 200L }, new long[]{ 2 });

// int8 (byte)
OrtValue v = OrtValue.fromByteArray(new byte[]{ 1, -1 }, new long[]{ 2 });

// bool (stored as 0/1 bytes)
OrtValue v = OrtValue.fromBooleanArray(new boolean[]{ true, false }, new long[]{ 2 });
```

> **Note:** The shape is expressed as a `long[]` where each element is the
> size of that dimension.  A value of `-1` denotes a dynamic (unknown) dimension.

#### Extracting data from output tensors

```java
OrtValue output = outputs[0];

// Check the type before extracting
TensorInfo ti = output.tensorInfo();
System.out.println(ti.elementType()); // e.g. FLOAT
System.out.println(Arrays.toString(ti.shape())); // e.g. [1, 1000]

// Extract by known element type
float[]  floats  = output.toFloatArray();
double[] doubles = output.toDoubleArray();
int[]    ints    = output.toIntArray();
long[]   longs   = output.toLongArray();
byte[]   bytes   = output.toByteArray();
String[] strings = output.toStringArray(); // for STRING tensors
```

#### Inspecting type and shape

```java
OnnxType kind = output.onnxType();   // TENSOR, SEQUENCE, MAP, …
boolean isTensor = output.isTensor();

TensorInfo ti = output.tensorInfo();
ElementType elemType  = ti.elementType(); // FLOAT, INT64, …
long[]      shape     = ti.shape();       // [-1, 3, 224, 224] for dynamic batch
int         rank      = ti.rank();        // number of dimensions
long        numElems  = ti.elementCount();// product of dims, or -1 if dynamic
boolean     isDynamic = ti.isDynamic();   // true if any dim is -1
```

---

### SessionOptions

`SessionOptions` is a fluent builder for session-level configuration.  It must
be closed after the session is created.

```java
try (var opts = new SessionOptions()) {

    // Thread counts (0 = let ORT decide)
    opts.setIntraOpNumThreads(4);   // parallelism within one operator
    opts.setInterOpNumThreads(2);   // parallelism across independent operators

    // Graph optimisation (higher = faster inference, slower first load)
    opts.setGraphOptimizationLevel(GraphOptimizationLevel.ENABLE_ALL);

    // Save the optimized graph to disk for faster future loads
    opts.setOptimizedModelFilePath("resnet50_opt.onnx");

    // Execution mode
    opts.setExecutionMode(ExecutionMode.SEQUENTIAL); // default
    opts.setExecutionMode(ExecutionMode.PARALLEL);

    // Memory management
    opts.enableCpuMemArena();    // pre-allocate an arena (default on)
    opts.disableMemPattern();    // disable pattern-based memory planning

    // Logging for this session
    opts.setLogId("my-session");
    opts.setLogSeverityLevel(LoggingLevel.WARNING);
    opts.setLogVerbosityLevel(0);

    // GPU providers (see "Execution Providers" section)
    opts.appendCudaExecutionProvider(0);

    // Low-level config entries
    opts.addConfigEntry("session.disable_prepacking", "1");

    try (var session = InferenceSession.create("model.onnx", opts)) {
        // ...
    }
}
```

#### GraphOptimizationLevel

| Level | Description |
|---|---|
| `DISABLE_ALL` | No optimisations; useful for debugging |
| `ENABLE_BASIC` | Constant folding, redundant node elimination |
| `ENABLE_EXTENDED` | Complex operator fusions |
| `ENABLE_LAYOUT` | Memory layout transformations |
| `ENABLE_ALL` | All optimisations (recommended for production) |

#### ExecutionMode

| Mode | When to use |
|---|---|
| `SEQUENTIAL` | Low-latency single-request serving (default) |
| `PARALLEL` | High-throughput batch workloads with parallelisable subgraphs |

---

### RunOptions

`RunOptions` configures a **single** call to `session.run()`.  Create one,
pass it to `run()`, then close it.

```java
try (var runOpts = new RunOptions()) {
    runOpts.setLogTag("request-42")
           .setLogSeverityLevel(LoggingLevel.WARNING)
           .setLogVerbosityLevel(0);

    OrtValue[] outputs = session.run(inputs, outputNames, runOpts);
}
```

#### Cancelling an in-progress run

`RunOptions.setTerminate()` signals ORT to abort the current run.  This is
useful for implementing request timeouts:

```java
var runOpts = new RunOptions();

// In another thread / via a ScheduledExecutorService:
scheduler.schedule(() -> runOpts.setTerminate(), 5, TimeUnit.SECONDS);

try {
    OrtValue[] outputs = session.run(inputs, outputNames, runOpts);
    // ...
} catch (OnnxException ex) {
    System.out.println("Run was cancelled: " + ex.getMessage());
} finally {
    runOpts.close();
}
```

---

### Environment

By default, each `InferenceSession.create(…)` call creates its own private
`OrtEnv`.  When you have **multiple sessions** in the same process, sharing
one `Environment` reduces thread-pool overhead.

```java
try (var env = new Environment(LoggingLevel.WARNING, "my-app")) {

    // All sessions share the same OrtEnv (and its thread pools)
    try (var s1 = env.createSession("model_a.onnx");
         var s2 = env.createSession("model_b.onnx");
         var s3 = env.createSession("model_c.onnx")) {

        // Run inference on all three in parallel
        // ...
    }
}
```

`Environment` also provides two static utility methods:

```java
// Print the ORT build version / commit
System.out.println(Environment.buildInfo());

// List execution providers compiled into this ORT binary
List<String> providers = Environment.availableProviders();
// e.g. ["CPUExecutionProvider", "CUDAExecutionProvider"]
System.out.println(providers);
```

Changing the log level at runtime:

```java
env.setLoggingLevel(LoggingLevel.VERBOSE);
```

---

## Model Introspection

### NodeInfo & TensorInfo

Before running inference you can inspect the model's inputs and outputs to
verify names, element types, and shapes.

```java
try (var session = InferenceSession.create("model.onnx")) {

    System.out.println("Inputs  (" + session.inputCount() + "):");
    for (NodeInfo ni : session.inputInfos()) {
        System.out.println("  " + ni.name() + " : " + ni.onnxType());
        if (ni.isTensor()) {
            TensorInfo ti = ni.tensorInfo();
            System.out.println("    element type : " + ti.elementType());
            System.out.println("    shape        : " + Arrays.toString(ti.shape()));
            System.out.println("    rank         : " + ti.rank());
            System.out.println("    dynamic?     : " + ti.isDynamic());
        }
    }

    System.out.println("Outputs (" + session.outputCount() + "):");
    for (NodeInfo ni : session.outputInfos()) {
        System.out.println("  " + ni);
    }

    // Convenience name lists
    List<String> inNames  = session.inputNames();
    List<String> outNames = session.outputNames();
}
```

Example output for ResNet-50:

```
Inputs  (1):
  data : TENSOR
    element type : FLOAT
    shape        : [-1, 3, 224, 224]
    rank         : 4
    dynamic?     : true
Outputs (1):
  resnetv24_dense0_fwd : TENSOR
```

### ModelMetadata

```java
ModelMetadata meta = session.metadata();
System.out.println("Producer : " + meta.producerName());  // e.g. "pytorch"
System.out.println("Graph    : " + meta.graphName());
System.out.println("Domain   : " + meta.domain());        // e.g. "ai.onnx"
System.out.println("Version  : " + meta.version());
System.out.println("Custom   : " + meta.customMetadata()); // Map<String,String>
```

---

## Data Types

### ElementType

Maps ONNX tensor element types to the ORT integer code:

| `ElementType` | Java primitive | Notes |
|---|---|---|
| `FLOAT` | `float` | 32-bit IEEE 754 |
| `DOUBLE` | `double` | 64-bit IEEE 754 |
| `INT8` | `byte` | Signed |
| `UINT8` | `byte` | Unsigned; use `& 0xFF` in Java |
| `INT16` | `short` | Signed |
| `UINT16` | `short` | Unsigned; use `& 0xFFFF` |
| `INT32` | `int` | Signed |
| `UINT32` | `int` | Unsigned; use `Integer.toUnsignedLong()` |
| `INT64` | `long` | Signed |
| `UINT64` | `long` | Unsigned; use `Long.toUnsignedString()` |
| `BOOL` | `byte` | Stored as 0 / 1 |
| `STRING` | `String` | UTF-8 |
| `FLOAT16` | — | No direct Java mapping |
| `BFLOAT16` | — | No direct Java mapping |
| `FLOAT8E4M3FN` … | — | 8-bit float variants |
| `INT4` / `UINT4` | — | 4-bit packed integers |

```java
ElementType et = ElementType.FLOAT;
System.out.println(et.value());     // 1
System.out.println(et.javaType());  // float

ElementType et = ElementType.of(7); // INT64
```

### OnnxType

Describes the kind of an `OrtValue`:

| `OnnxType` | Description |
|---|---|
| `TENSOR` | Dense tensor (most common) |
| `SEQUENCE` | Sequence of values |
| `MAP` | Key-value map |
| `SPARSE_TENSOR` | Sparse tensor |
| `OPTIONAL` | Optional value |
| `OPAQUE` | Opaque/custom type |

---

## Execution Providers (GPU Acceleration)

Execution providers let ORT offload work to GPUs or specialized accelerators.
They are configured on `SessionOptions` and tried in registration order; ORT
falls back to the CPU provider if a requested provider is not available.

```java
try (var opts = new SessionOptions()) {

    // CUDA (NVIDIA GPU), device 0
    opts.appendCudaExecutionProvider(0);

    // TensorRT (NVIDIA GPU with TRT), device 0
    opts.appendTensorRTExecutionProvider(0);

    // ROCM (AMD GPU), device 0
    opts.appendRocmExecutionProvider(0);

    // DirectML (Windows GPU — NVIDIA, AMD, Intel)
    opts.appendDirectMLExecutionProvider(0);

    try (var session = InferenceSession.create("model.onnx", opts)) {
        // Inference runs on GPU if the provider compiled in
    }
}
```

Check what is available on the current installation:

```java
List<String> available = Environment.availableProviders();
System.out.println(available);
// [CUDAExecutionProvider, CPUExecutionProvider]
```

> **Tip:** Always register GPU providers **before** CPU so that ORT prefers
> them.  The CPU provider is always implicitly available and does not need to
> be added manually.

---

## Error Handling

All ORT errors surface as `OnnxException` (an unchecked
`RuntimeException`), which carries the ORT error code alongside the message:

```java
try (var session = InferenceSession.create("model.onnx")) {
    OrtValue[] outputs = session.run(inputs);
    // ...
} catch (OnnxException ex) {
    System.err.println("ORT error code : " + ex.errorCode());
    System.err.println("Message        : " + ex.getMessage());
}
```

Common error codes:

| Code | Meaning |
|---|---|
| 1 | `FAIL` — general failure |
| 2 | `INVALID_ARGUMENT` — wrong input name, shape mismatch, etc. |
| 3 | `NO_SUCHFILE` — model file not found |
| 4 | `NO_MODEL` — invalid or corrupt model |
| 6 | `ENGINE_ERROR` — provider-specific runtime error |
| 7 | `RUNTIME_EXCEPTION` — internal ORT exception |

---

## Resource Management

All three main resource-holding classes implement `AutoCloseable`.  Use
try-with-resources to guarantee timely release of native memory:

```java
try (var opts    = new SessionOptions();          // 1. options first
     var session = InferenceSession.create(path, opts)) {  // 2. then session

    try (OrtValue input = OrtValue.fromFloatArray(data, shape)) {

        OrtValue[] outputs = session.run(Map.of("input", input));
        try {
            // consume outputs…
        } finally {
            // 3. always close ORT-allocated output tensors
            for (OrtValue v : outputs) v.close();
        }
    }
}
```

**Important rules:**
- Close `SessionOptions` **after** the session is created (not before).
- Close `RunOptions` **after** `run()` returns.
- Close every `OrtValue` in `outputs[]` — ORT allocates them on the native
  heap and they are not garbage-collected.
- `InferenceSession` is thread-safe; `OrtValue` is **not** — do not share an
  input or output tensor between threads.

---

## Logging

Logging is controlled at two levels: the `Environment` level (global minimum)
and the `SessionOptions` / `RunOptions` levels (per-session / per-run).

```java
// Global minimum — only WARNING and above are printed
try (var env = new Environment(LoggingLevel.WARNING, "my-app")) {

    // Verbose for a specific session
    try (var opts = new SessionOptions()) {
        opts.setLogSeverityLevel(LoggingLevel.VERBOSE)
            .setLogVerbosityLevel(5)
            .setLogId("debug-session");
        // ...
    }
}
```

| `LoggingLevel` | Meaning |
|---|---|
| `VERBOSE` | All messages including trace-level detail |
| `INFO` | Informational messages |
| `WARNING` | Warnings (default) |
| `ERROR` | Errors only |
| `FATAL` | Fatal errors only |

---

## Performance Tuning

| Concern | Recommendation                                                                                           |
|---|----------------------------------------------------------------------------------------------------------|
| First-load latency | Use `GraphOptimizationLevel.ENABLE_ALL` and save the optimized model with `setOptimizedModelFilePath`   |
| Throughput (single process) | Use `ExecutionMode.PARALLEL` and tune `setInterOpNumThreads`                                             |
| Latency (single request) | Use `ExecutionMode.SEQUENTIAL` (default); tune `setIntraOpNumThreads`                                    |
| Many models in one process | Share a single `Environment` to avoid creating redundant thread pools                                    |
| Memory | Call `disableCpuMemArena()` and `disableMemPattern()` to minimise peak memory at the cost of some speed  |
| Benchmarking | Call `enableProfiling("profile_run")` to generate a Chrome-trace JSON you can load at `chrome://tracing` |

---

## Tutorials

### Tutorial 1: Image Classification with ResNet-50

This tutorial shows end-to-end inference for ResNet-50, a popular
1 000-class image classifier.

```java
import smile.onnx.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.*;
import java.io.*;
import java.util.*;

public class ResNet50Example {

    /** ImageNet mean and std for channel normalization. */
    static final float[] MEAN = { 0.485f, 0.456f, 0.406f };
    static final float[] STD  = { 0.229f, 0.224f, 0.225f };

    public static void main(String[] args) throws Exception {
        String modelPath = "resnet50.onnx";
        String imagePath = "cat.jpg";

        // 1. Pre-process image → float[1][3][224][224] in NCHW order
        float[] pixels = preprocess(imagePath, 224, 224);
        long[]  shape  = { 1, 3, 224, 224 };

        // 2. Load the model
        try (var session = InferenceSession.create(modelPath)) {

            // 3. Inspect the model (optional, useful during development)
            System.out.println("Input  : " + session.inputInfos().getFirst());
            System.out.println("Output : " + session.outputInfos().getFirst());

            // 4. Build the input map
            try (OrtValue input = OrtValue.fromFloatArray(pixels, shape)) {
                String inputName = session.inputNames().getFirst();

                // 5. Run inference
                OrtValue[] outputs = session.run(Map.of(inputName, input));
                try {
                    // 6. Post-process: softmax → argmax
                    float[] logits = outputs[0].toFloatArray();
                    float[] probs  = softmax(logits);
                    int     classId = argmax(probs);
                    System.out.printf("Predicted class: %d  (score: %.4f)%n",
                                      classId, probs[classId]);
                } finally {
                    for (OrtValue v : outputs) v.close();
                }
            }
        }
    }

    static float[] preprocess(String path, int width, int height) throws IOException {
        BufferedImage src = ImageIO.read(new File(path));
        // Resize to target dimensions
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        img.createGraphics().drawImage(
            src.getScaledInstance(width, height, Image.SCALE_SMOOTH), 0, 0, null);

        float[] data = new float[3 * height * width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = img.getRGB(x, y);
                int idx = y * width + x;
                data[0 * height * width + idx] = (((rgb >> 16) & 0xFF) / 255f - MEAN[0]) / STD[0];
                data[1 * height * width + idx] = (((rgb >>  8) & 0xFF) / 255f - MEAN[1]) / STD[1];
                data[2 * height * width + idx] = (((rgb      ) & 0xFF) / 255f - MEAN[2]) / STD[2];
            }
        }
        return data;
    }

    static float[] softmax(float[] x) {
        float max = Float.NEGATIVE_INFINITY;
        for (float v : x) if (v > max) max = v;
        float sum = 0;
        float[] out = new float[x.length];
        for (int i = 0; i < x.length; i++) { out[i] = (float) Math.exp(x[i] - max); sum += out[i]; }
        for (int i = 0; i < out.length; i++) out[i] /= sum;
        return out;
    }

    static int argmax(float[] x) {
        int idx = 0;
        for (int i = 1; i < x.length; i++) if (x[i] > x[idx]) idx = i;
        return idx;
    }
}
```

---

### Tutorial 2: NLP with a BERT Tokenizer Model

Many NLP models take `INT64` input IDs.  This tutorial shows how to pass
tokenised text to such a model.

```java
import smile.onnx.*;
import java.util.*;

public class BertExample {

    public static void main(String[] args) throws Exception {
        // BERT-base-uncased ONNX (obtained from Hugging Face Optimum, etc.)
        try (var session = InferenceSession.create("bert-base-uncased.onnx")) {

            // Inspect inputs — typical BERT inputs:
            //   input_ids      : INT64 [batch, sequence]
            //   attention_mask : INT64 [batch, sequence]
            //   token_type_ids : INT64 [batch, sequence]
            for (NodeInfo ni : session.inputInfos()) {
                System.out.println(ni.name() + " -> " + ni.tensorInfo());
            }

            int batchSize = 1;
            int seqLen    = 128;

            // Tokenise (replace with your tokeniser output)
            long[] inputIds     = new long[batchSize * seqLen]; // padded token IDs
            long[] attentionMask = new long[batchSize * seqLen];
            long[] tokenTypeIds  = new long[batchSize * seqLen];

            // Fill in real token IDs here …
            Arrays.fill(attentionMask, 1L); // 1 = real token, 0 = padding

            long[] shape = { batchSize, seqLen };

            try (OrtValue ids   = OrtValue.fromLongArray(inputIds,      shape);
                 OrtValue mask  = OrtValue.fromLongArray(attentionMask, shape);
                 OrtValue types = OrtValue.fromLongArray(tokenTypeIds,  shape)) {

                Map<String, OrtValue> inputs = Map.of(
                    "input_ids",      ids,
                    "attention_mask", mask,
                    "token_type_ids", types
                );

                OrtValue[] outputs = session.run(inputs);
                try {
                    // last_hidden_state: FLOAT [batch, seq, hidden]
                    float[] hidden = outputs[0].toFloatArray();
                    System.out.println("Hidden state size: " + hidden.length);
                } finally {
                    for (OrtValue v : outputs) v.close();
                }
            }
        }
    }
}
```

---

### Tutorial 3: Sharing an Environment across Sessions

When deploying multiple models in the same JVM (e.g. a model serving
micro-service), share one `Environment` to reduce thread-pool overhead.

```java
import smile.onnx.*;
import java.util.*;
import java.util.concurrent.*;

public class MultiModelServer {

    public static void main(String[] args) throws Exception {
        // One environment for all sessions
        try (var env = new Environment(LoggingLevel.WARNING, "model-server")) {

            System.out.println("ORT build : " + Environment.buildInfo());
            System.out.println("Providers : " + Environment.availableProviders());

            try (var opts = new SessionOptions()) {
                opts.setGraphOptimizationLevel(GraphOptimizationLevel.ENABLE_ALL)
                    .setIntraOpNumThreads(4);

                // Load two models into the shared environment
                try (InferenceSession detector   = env.createSession("detector.onnx",    opts);
                     InferenceSession classifier = env.createSession("classifier.onnx",  opts)) {

                    // Both sessions share the OrtEnv thread pools
                    ExecutorService pool = Executors.newFixedThreadPool(8);

                    // Process 100 requests concurrently
                    List<Future<?>> futures = new ArrayList<>();
                    for (int i = 0; i < 100; i++) {
                        futures.add(pool.submit(() -> {
                            float[] img   = loadImage();          // your loader
                            long[]  shape = { 1, 3, 640, 640 };
                            try (OrtValue input = OrtValue.fromFloatArray(img, shape)) {
                                OrtValue[] det = detector.run(
                                        Map.of("images", input));
                                // … pass boxes to classifier …
                                for (OrtValue v : det) v.close();
                            }
                        }));
                    }
                    for (var f : futures) f.get();
                    pool.shutdown();
                }
            }
        }
    }

    static float[] loadImage() { return new float[3 * 640 * 640]; }
}
```

---

### Tutorial 4: GPU Inference with CUDA

```java
import smile.onnx.*;
import java.util.Map;

public class GpuExample {

    public static void main(String[] args) throws Exception {
        // Check what providers are available
        List<String> providers = Environment.availableProviders();
        System.out.println("Available providers: " + providers);

        try (var opts = new SessionOptions()) {

            if (providers.contains("CUDAExecutionProvider")) {
                // Prefer CUDA on device 0; fall back to CPU automatically
                opts.appendCudaExecutionProvider(0);
                System.out.println("Using CUDA GPU");
            } else {
                System.out.println("CUDA not available — using CPU");
            }

            opts.setGraphOptimizationLevel(GraphOptimizationLevel.ENABLE_ALL)
                .setIntraOpNumThreads(1); // thread count less relevant on GPU

            try (var session = InferenceSession.create("resnet50.onnx", opts)) {
                float[] data  = new float[3 * 224 * 224];
                long[]  shape = { 1, 3, 224, 224 };

                try (OrtValue input = OrtValue.fromFloatArray(data, shape)) {
                    OrtValue[] outputs = session.run(
                            Map.of(session.inputNames().getFirst(), input));
                    try {
                        float[] scores = outputs[0].toFloatArray();
                        System.out.println("Output length: " + scores.length);
                    } finally {
                        for (OrtValue v : outputs) v.close();
                    }
                }
            }
        }
    }
}
```

---

### Tutorial 5: Loading a Model from a JAR Resource

Embedding models inside a JAR (or a Spring Boot fat-JAR) is a common
pattern for self-contained applications.

```java
import smile.onnx.*;
import java.util.Map;

public class EmbeddedModelExample {

    public static void main(String[] args) throws Exception {
        // Load model bytes from classpath resource
        byte[] modelBytes;
        try (var is = EmbeddedModelExample.class
                         .getResourceAsStream("/models/squeezenet.onnx")) {
            if (is == null) throw new IllegalStateException("Model not found in JAR");
            modelBytes = is.readAllBytes();
        }

        // Create session from bytes — no temp file needed
        try (var session = InferenceSession.create(modelBytes)) {
            System.out.println("Loaded: " + session.inputInfos());

            float[] data  = new float[3 * 224 * 224];
            long[]  shape = { 1, 3, 224, 224 };

            try (OrtValue input = OrtValue.fromFloatArray(data, shape)) {
                OrtValue[] outputs = session.run(
                        Map.of(session.inputNames().getFirst(), input));
                try {
                    System.out.println("Output shape: " +
                        java.util.Arrays.toString(outputs[0].tensorInfo().shape()));
                } finally {
                    for (OrtValue v : outputs) v.close();
                }
            }
        }
    }
}
```

---

### Tutorial 6: Profiling an Inference Session

ORT can produce a Chrome-trace JSON file that shows how long each operator
took.  Open it in `chrome://tracing` or [Perfetto UI](https://ui.perfetto.dev).

```java
import smile.onnx.*;
import java.util.Map;

public class ProfilingExample {

    public static void main(String[] args) throws Exception {
        try (var opts = new SessionOptions()) {
            // Profiling data is written to  "my_profile_<timestamp>.json"
            opts.enableProfiling("my_profile");
            opts.setGraphOptimizationLevel(GraphOptimizationLevel.ENABLE_BASIC);
            // Note: use DISABLE_ALL or ENABLE_BASIC when profiling so that
            // the profile reflects the unoptimized operator graph.

            try (var session = InferenceSession.create("resnet50.onnx", opts)) {
                float[] data  = new float[3 * 224 * 224];
                long[]  shape = { 1, 3, 224, 224 };

                // Warm-up run
                try (OrtValue input = OrtValue.fromFloatArray(data, shape)) {
                    OrtValue[] warmup = session.run(
                            Map.of(session.inputNames().getFirst(), input));
                    for (OrtValue v : warmup) v.close();
                }

                // Timed run
                long start = System.nanoTime();
                for (int i = 0; i < 100; i++) {
                    try (OrtValue input = OrtValue.fromFloatArray(data, shape)) {
                        OrtValue[] out = session.run(
                                Map.of(session.inputNames().getFirst(), input));
                        for (OrtValue v : out) v.close();
                    }
                }
                long elapsed = System.nanoTime() - start;
                System.out.printf("Average latency: %.2f ms%n", elapsed / 1e6 / 100);
            }
            // Profile JSON written when session closes
        }
        System.out.println("Profile written — open in chrome://tracing");
    }
}
```

---

### Tutorial 7: Cancelling a Long-Running Inference

For services with strict SLAs you can cancel an in-flight inference from
another thread using `RunOptions.setTerminate()`.

```java
import smile.onnx.*;
import java.util.*;
import java.util.concurrent.*;

public class CancellableInferenceExample {

    public static void main(String[] args) throws Exception {
        try (var session = InferenceSession.create("large_model.onnx")) {

            float[] data  = new float[3 * 224 * 224];
            long[]  shape = { 1, 3, 224, 224 };

            var runOpts = new RunOptions();
            runOpts.setLogTag("cancellable-run");

            // Schedule a cancellation after 2 seconds
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.schedule(() -> {
                System.out.println("Timeout reached — cancelling inference");
                runOpts.setTerminate();
            }, 2, TimeUnit.SECONDS);

            try (OrtValue input = OrtValue.fromFloatArray(data, shape)) {
                try {
                    OrtValue[] outputs = session.run(
                            Map.of(session.inputNames().getFirst(), input),
                            session.outputNames().toArray(new String[0]),
                            runOpts);
                    System.out.println("Inference completed");
                    for (OrtValue v : outputs) v.close();
                } catch (OnnxException ex) {
                    System.out.println("Inference cancelled or failed: " + ex.getMessage());
                }
            } finally {
                runOpts.close();
                scheduler.shutdown();
            }
        }
    }
}
```

---

## API Quick Reference

### `InferenceSession`

| Method | Description |
|---|---|
| `create(String path)` | Load model from file with default options |
| `create(String path, SessionOptions opts)` | Load model from file |
| `create(byte[] bytes)` | Load model from byte array |
| `create(byte[] bytes, SessionOptions opts)` | Load model from byte array |
| `run(Map<String,OrtValue>)` | Run all outputs |
| `run(Map<String,OrtValue>, String[])` | Run selected outputs |
| `run(Map<String,OrtValue>, String[], RunOptions)` | Run with per-call options |
| `inputCount()` | Number of model inputs |
| `outputCount()` | Number of model outputs |
| `inputInfos()` | `List<NodeInfo>` for inputs |
| `outputInfos()` | `List<NodeInfo>` for outputs |
| `inputNames()` | `List<String>` of input names |
| `outputNames()` | `List<String>` of output names |
| `metadata()` | `ModelMetadata` |
| `close()` | Release native resources |

### `OrtValue`

| Method | Description |
|---|---|
| `fromFloatArray(float[], long[])` | Create FLOAT tensor |
| `fromDoubleArray(double[], long[])` | Create DOUBLE tensor |
| `fromIntArray(int[], long[])` | Create INT32 tensor |
| `fromLongArray(long[], long[])` | Create INT64 tensor |
| `fromByteArray(byte[], long[])` | Create INT8 tensor |
| `fromBooleanArray(boolean[], long[])` | Create BOOL tensor |
| `toFloatArray()` | Extract FLOAT data |
| `toDoubleArray()` | Extract DOUBLE data |
| `toIntArray()` | Extract INT32 data |
| `toLongArray()` | Extract INT64 data |
| `toByteArray()` | Extract INT8 / UINT8 / BOOL data |
| `toStringArray()` | Extract STRING data |
| `onnxType()` | `OnnxType` of this value |
| `isTensor()` | `true` if type is TENSOR |
| `tensorInfo()` | `TensorInfo` (shape + element type) |
| `close()` | Release native resources |

### `SessionOptions`

| Method | Description                         |
|---|-------------------------------------|
| `setIntraOpNumThreads(int)` | Threads per operator (0 = auto)     |
| `setInterOpNumThreads(int)` | Threads across operators (0 = auto) |
| `setGraphOptimizationLevel(GraphOptimizationLevel)` | Optimisation depth                  |
| `setExecutionMode(ExecutionMode)` | SEQUENTIAL or PARALLEL              |
| `setLogId(String)` | Session log identifier              |
| `setLogSeverityLevel(LoggingLevel)` | Minimum log severity                |
| `setLogVerbosityLevel(int)` | Verbosity (0 = default)             |
| `setOptimizedModelFilePath(String)` | Save optimized graph to file       |
| `enableCpuMemArena()` / `disableCpuMemArena()` | CPU memory arena                    |
| `enableMemPattern()` / `disableMemPattern()` | Memory pattern planner              |
| `enableProfiling(String)` / `disableProfiling()` | Chrome-trace profiling              |
| `appendCudaExecutionProvider(int)` | Add CUDA EP                         |
| `appendTensorRTExecutionProvider(int)` | Add TensorRT EP                     |
| `appendRocmExecutionProvider(int)` | Add ROCM EP                         |
| `appendDirectMLExecutionProvider(int)` | Add DirectML EP (Windows)           |
| `addConfigEntry(String, String)` | Low-level key-value config          |
| `close()` | Release native resources            |

### `RunOptions`

| Method | Description |
|---|---|
| `setLogTag(String)` | Tag for this run's log messages |
| `setLogSeverityLevel(LoggingLevel)` | Minimum log severity |
| `setLogVerbosityLevel(int)` | Verbosity level |
| `setTerminate()` | Request cancellation of the current run |
| `unsetTerminate()` | Clear the cancellation flag |
| `addConfigEntry(String, String)` | Low-level key-value config |
| `close()` | Release native resources |

### `Environment`

| Method | Description |
|---|---|
| `new Environment()` | Default (WARNING, "smile-onnx") |
| `new Environment(LoggingLevel, String)` | Custom level and log ID |
| `static buildInfo()` | ORT version / build string |
| `static availableProviders()` | Provider names compiled into ORT |
| `setLoggingLevel(LoggingLevel)` | Update log level at runtime |
| `createSession(String)` | Create session (default options) |
| `createSession(String, SessionOptions)` | Create session |
| `createSession(byte[])` | Create session from bytes |
| `createSession(byte[], SessionOptions)` | Create session from bytes |
| `close()` | Release native OrtEnv |

### `TensorInfo`

| Method | Description |
|---|---|
| `elementType()` | `ElementType` |
| `shape()` | `long[]` of dimension sizes; `-1` = dynamic |
| `rank()` | Number of dimensions |
| `elementCount()` | Product of all dims, or `-1` if dynamic |
| `isDynamic()` | `true` if any dimension is `-1` |

### `ModelMetadata`

| Accessor | Description |
|---|---|
| `producerName()` | Producing tool (e.g. `"pytorch"`) |
| `graphName()` | Main graph name |
| `graphDescription()` | Human-readable graph description |
| `domain()` | Model domain (e.g. `"ai.onnx"`) |
| `description()` | Human-readable model description |
| `version()` | Model version (`long`) |
| `customMetadata()` | `Map<String,String>` of user-defined entries |

---

*SMILE — © 2010-2026 Haifeng Li. GNU GPL licensed.*

