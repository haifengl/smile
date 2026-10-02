# SMILE — Deep Learning

The `smile-deep` module provides idiomatic Java API for deep learning
on the JVM while still reaching CPU, CUDA, and MPS backends by wrapping
the PyTorch / LibTorch C++ runtime. It also provides tiktoken BPE tokenizer,
LLaMA-3 inference, EfficientNet-V2, an image classification pipeline, and
ONNX Runtime inference (`smile.onnx`) out of the box — plus ONNX GenAI
(`smile.onnx.genai`) for generative models.

## Related guides

| Guide | Covers |
|---|---|
| [LLM.md](LLM.md) | `smile.llm` — tokenizer, LLaMA-3, Qwen3.5, inference engine, KV cache, quantization, tensor parallelism |
| [VISION.md](VISION.md) | `smile.vision` — transforms, `ImageDataset`, EfficientNet-V2, ImageNet labels |
| [ONNX.md](ONNX.md) | `smile.onnx` — ONNX Runtime sessions, tensors, execution providers |
| [ONNX_GENAI.md](ONNX_GENAI.md) | `smile.onnx.genai` — generate loop, chat model, multimodal |

---

## Table of Contents

1. [Prerequisites & Dependencies](#prerequisites--dependencies)
2. [Module Structure](#module-structure)
3. [Tensors (`smile.deep.tensor`)](#tensors-smiledeeptensor)
   - [Factory Methods](#factory-methods)
   - [Indexing](#indexing)
   - [Arithmetic & Math](#arithmetic--math)
   - [Tensor Scope (Memory Management)](#tensor-scope-memory-management)
   - [dtype / device Control](#dtype--device-control)
4. [Layers (`smile.deep.layer`)](#layers-smiledeeplayer)
   - [Dense / Activation Shortcuts](#dense--activation-shortcuts)
   - [Convolutional Layers](#convolutional-layers)
   - [Pooling Layers](#pooling-layers)
   - [Normalization Layers](#normalization-layers)
   - [Dropout & Embedding](#dropout--embedding)
   - [Sequential Composition](#sequential-composition)
5. [Activation Functions (`smile.deep.activation`)](#activation-functions-smiledeepactivation)
6. [Loss Functions (`smile.deep.Loss`)](#loss-functions-smiledeeploss)
7. [Optimizers (`smile.deep.Optimizer`)](#optimizers-smiledeepoptimizer)
8. [Model API (`smile.deep.Model`)](#model-api-smiledeepmodel)
9. [Metrics (`smile.deep.metric`)](#metrics-smiledeepmetric)
10. [Data Loading (`smile.deep.Dataset`)](#data-loading-smiledeepdataset)
11. [CUDA Utilities (`smile.deep.CUDA`)](#cuda-utilities-smiledeepcuda)
12. [End-to-End Examples](#end-to-end-examples)
    - [Training a LeNet on MNIST](#training-a-lenet-on-mnist)
    - [CPU-only MLP Training](#cpu-only-mlp-training)

---

## Prerequisites & Dependencies

```kotlin
// build.gradle.kts (consumer module)
dependencies {
    implementation("com.github.haifengl:smile-deep:6.x.x")
}
```

Runtime requirements:

- Java 25 or newer.
- The native `smile_torch` shared library and its LibTorch dependencies must be
  discoverable by the platform loader:
  - **Windows:** `PATH` (the FFM bindings load by bare name, so a stale copy in
    `System32` can win the DLL search order; `smile.onnx.NativeLibrary` preloads
    the intended copy by absolute path first).
  - **Linux:** `LD_LIBRARY_PATH`
  - **macOS:** do **not** rely on `DYLD_LIBRARY_PATH`. The FFM bindings call
    `dlopen` on a bare name via `SymbolLookup.libraryLookup`, which ignores
    `java.library.path` and `LD_LIBRARY_PATH`; and macOS System Integrity
    Protection (SIP) strips `DYLD_*` variables when the JVM is started through
    the `/usr/bin/java` stub, so dyld never sees them. Smile Studio and the
    launcher instead preload these natives by absolute path
    (`smile.onnx.NativeLibrary`, and the macOS block in the launcher/predef).
- For ONNX inference (`smile.onnx`), the ONNX Runtime shared library
  (`onnxruntime.dll` / `libonnxruntime.so` / `libonnxruntime.dylib`) is resolved
  by `smile.onnx.NativeLibrary` from, in order: the `onnxruntime.native.path`
  system property, the `ONNXRUNTIME_NATIVE_PATH` environment variable, then the
  OS library search path ([ORT releases](https://github.com/microsoft/onnxruntime/releases)).
- For ONNX GenAI (`smile.onnx.genai`), place `onnxruntime-genai` likewise and
  point `onnxruntime-genai.native.path` / `ONNXRUNTIME_GENAI_NATIVE_PATH` at it
  ([GenAI releases](https://github.com/microsoft/onnxruntime-genai/releases)).
- When launching outside Gradle or Smile Studio, enable FFM access explicitly:

```text
--enable-native-access=ALL-UNNAMED
```

SMILE's own Gradle test configuration also adds:

```text
--add-opens=java.base/java.nio=ALL-UNNAMED
```

If you run `smile-deep` from a custom launcher and hit native-access or buffer
interop errors, mirror that setting as well. In this repository, tests point
the native loader at `studio/src/universal/bin` and `studio/src/universal/libtorch`.

---

## Module Structure

```
smile.deep
├── tensor/        Tensor class, Index, Device, DeviceType, ScalarType, Layout
├── layer/         Layer interface and all built-in layer implementations
├── activation/    ActivationFunction and ~14 activation modules
├── metric/        Accuracy, Precision, Recall, F1Score, Averaging
├── Loss.java      Static factory for all standard loss functions
├── Optimizer.java Static factory for SGD, Adam, AdamW, RMSprop
├── Model.java     Abstract base class for trainable models
├── Dataset.java   Dataset interface
├── DatasetImpl.java, DataSampler.java, SampleBatch.java
└── CUDA.java      GPU info helpers

smile.torch
├── smile_torch_h.java  FFM downcalls for the C ABI
└── Native.java         Cleaner/error-handling helpers over raw FFM bindings

smile.llm
├── tokenizer/     Tokenizer interface + Tiktoken (BPE) implementation
├── transformer/   Shared primitives: Attention, FeedForward,
│                  PositionalEncoding, RotaryPositionalEncoding
├── llama/         LLaMA-3: Llama, LlamaModel, LlamaBlock,
│                  GroupedQueryAttention, LlamaModelArgs, Tokenizer
├── qwen/          Qwen3.5 hybrid text stack
├── Message.java   Immutable dialog message (role + content)
├── Role.java      system / user / assistant / tool
├── ChatCompletion.java  Inference result record
├── FinishReason.java    stop / length / tool_calls

smile.vision
├── transform/     Transform interface, ImageClassification pipeline
├── layer/         Vision-specific blocks: MBConv, FusedMBConv,
│                  Conv2dNormActivation, SqueezeExcitation, StochasticDepth
├── EfficientNet.java   EfficientNet-V2 architecture + pretrained factory methods
├── VisionModel.java    Model subclass coupling a LayerBlock with a Transform
├── ImageDataset.java   Folder-per-class dataset with background prefetch
└── ImageNet.java       1000-class ImageNet label/folder arrays + utilities

smile.onnx
├── foreign/       Panama FFM bindings to the ONNX Runtime C API
├── InferenceSession.java  Load and run ONNX models
├── OrtValue.java          Tensor / sequence / map containers
├── SessionOptions.java    Threads, graph opts, execution providers
├── RunOptions.java        Per-run log tag, severity, cancellation
├── Environment.java       Shared OrtEnv / thread pools
└── ModelMetadata.java, NodeInfo.java, TensorInfo.java, …

smile.onnx.genai
├── foreign/       Panama FFM bindings to onnxruntime-genai
├── Model / Tokenizer / Generator / SimpleGenAI
└── GenAiChatModel.java    LanguageModel adapter for serve
```

The native side lives in `deep/src/main/cpp` and exposes a compact C ABI
(`smile_torch`) over LibTorch. This hourglass layer keeps the Java API on top
of FFM while isolating the higher-level code from LibTorch's C++ ABI.
ONNX Runtime is linked separately through `smile.onnx.foreign`.

---

## Tensors (`smile.deep.tensor`)

`Tensor` is the central data structure — a multidimensional array backed by a
native LibTorch tensor.  It implements `AutoCloseable`; always close tensors
(or use a scope) when they are no longer needed to avoid native memory leaks.

### Factory Methods

```java
// Zeros / ones
Tensor z = Tensor.zeros(3, 4);         // shape [3,4], float32
Tensor o = Tensor.ones(2, 3);

// Random
Tensor r  = Tensor.rand(5, 5);         // uniform [0,1)
Tensor rn = Tensor.randn(5, 5);        // standard normal

// From Java arrays
float[] data = {1f, 2f, 3f, 4f};
Tensor t = Tensor.of(data, 2, 2);      // shape [2,2]

long[]  ldata = {0L, 1L, 2L};
Tensor li = Tensor.of(ldata, 3);       // Int64 tensor

// Arange
Tensor ar = Tensor.arange(0, 10, 1);   // [0,1,...,9]

// Eye (identity matrix)
Tensor eye = Tensor.eye(4);
```

### Indexing

`smile.deep.tensor.Index` provides Python-style index objects:

```java
Tensor t = Tensor.rand(4, 4);

Tensor col1 = t.get(Index.Colon, Index.of(1));  // all rows, column 1 → shape [4]
Tensor row2 = t.get(Index.of(2));               // row 2 → shape [4]
Tensor sub  = t.get(Index.Slice(1, 3));         // rows 1–2 → shape [2, 4]
Tensor last = t.get(Index.Ellipsis, Index.of(3)); // last col via ellipsis
Tensor newDim = t.get(Index.None, Index.of(0)); // insert batch dim → shape [1, 4]

// Index with another tensor
int[] rows = {0, 2};
Tensor rowIdx = Tensor.of(rows, 2);
Tensor subset = t.get(rowIdx);                  // rows 0 and 2 → shape [2, 4]
```

### Arithmetic & Math

```java
Tensor a = Tensor.ones(3);
Tensor b = Tensor.ones(3).mul(2.0);

// Non-mutating (returns new tensor)
Tensor sum  = a.add(b);
Tensor diff = a.sub(1.0f);    // sub(float) or sub(double) — non-mutating
Tensor prod = a.mul(3.0);
Tensor quot = a.div(2.0);

// In-place (trailing underscore — returns 'this')
a.add_(1.0);
a.sub_(0.5f);   // sub_(float) — mutates in place
a.mul_(2.0);
a.exp_();       // e^x in place
a.fill_(0.0f);

// Reduction
double s = a.sum().doubleValue();
Tensor argmax = a.argmax(0, false);   // index of max along dim 0
Tensor topk2  = a.topk(2, 0, true, true).get0(); // top-2 values

// Shape utilities
long[] shape = a.shape();
int    rank  = a.dim();
long   rows  = a.size(0);
Tensor flat  = a.view(-1);
Tensor t2d   = flat.reshape(3, 1);
Tensor tr    = t2d.t();           // transpose
Tensor contig = tr.contiguous();  // force contiguous memory layout

// Type casting
Tensor fp16 = a.to(ScalarType.Float16);
Tensor onCuda = a.to(new Device(DeviceType.CUDA, 0));
```

### Tensor Scope (Memory Management)

Use `AutoScope` to batch-free many tensors at once:

```java
try (var scope = new smile.util.AutoScope()) {
    Tensor.push(scope);
    // ... all tensors created here are tracked
    Tensor result = computeSomething();
    result.retain(); // keep this one after scope exit
    Tensor.pop();    // closes all tracked tensors except retained ones
}
```

For inference loops you can also use `Tensor.noGradGuard()`:

```java
try (var guard = Tensor.noGradGuard()) {
    Tensor output = model.forward(input);
    // no gradient graph is built → lower memory usage
}
```

### dtype / device Control

```java
// Set global defaults (affects all subsequent factory calls)
Tensor.setDefaultOptions(new Options()
        .dtype(ScalarType.Float32)
        .device(Device.ofCPU()));

// Per-tensor override
Tensor t = Tensor.ones(new Options().dtype(ScalarType.Float64), 3, 3);
```

---

## Layers (`smile.deep.layer`)

All layers implement the `Layer` interface:

```java
public interface Layer extends Function<Tensor, Tensor> {
    Tensor forward(Tensor input);
    MemorySegment module();      // native ST_Module handle
    String name();               // native/fallback module name
    Layer to(Device device);     // move to another device
}
```

### Dense / Activation Shortcuts

`Layer` provides convenience factories that combine a `LinearLayer` with an
activation in a single `SequentialBlock`:

```java
LinearLayer fc   = Layer.linear(128, 64);        // no activation
SequentialBlock r  = Layer.relu(128, 64);         // Linear + ReLU
SequentialBlock rd = Layer.relu(128, 64, 0.2);   // Linear + ReLU + Dropout(0.2)
SequentialBlock g  = Layer.gelu(128, 64);
SequentialBlock s  = Layer.silu(128, 64);
SequentialBlock t  = Layer.tanh(128, 64);
SequentialBlock sg = Layer.sigmoid(128, 64);
SequentialBlock ls = Layer.logSoftmax(128, 64);
SequentialBlock lk = Layer.leaky(128, 64, 0.01); // LeakyReLU
```

### Convolutional Layers

```java
// Simple conv with kernel 3, stride 1, no padding
Conv2dLayer c1 = Layer.conv2d(3, 32, 3);

// Full control: in, out, kernel, stride, padding, dilation, groups, bias, paddingMode
Conv2dLayer c2 = Layer.conv2d(3, 32, 3, 1, 1, 1, 1, true, "zeros"); // same padding

// String padding ("valid" or "same")
Conv2dLayer c3 = Layer.conv2d(3, 32, 3, 1, "same", 1, 1, true, "zeros");
```

### Pooling Layers

```java
MaxPool2dLayer       mp = Layer.maxPool2d(2);        // 2×2 max pooling
AvgPool2dLayer       ap = Layer.avgPool2d(2);
AdaptiveAvgPool2dLayer aa = Layer.adaptiveAvgPool2d(1); // output 1×1 (global)
```

### Normalization Layers

```java
BatchNorm1dLayer bn1 = Layer.batchNorm1d(64);
BatchNorm2dLayer bn2 = Layer.batchNorm2d(32);

// Group Norm — 4 groups over 32 channels
GroupNormLayer gn = Layer.groupNorm(4, 32);

// RMS Norm — normalizes last dimension
RMSNormLayer rms = Layer.rmsNorm(64);
```

### Dropout & Embedding

```java
DropoutLayer  drop = Layer.dropout(0.3);
EmbeddingLayer emb = Layer.embedding(50000, 256);        // vocab=50k, dim=256
EmbeddingLayer emb2 = Layer.embedding(50000, 256, 1.0);  // with scale alpha
```

### Sequential Composition

```java
// Build a small MLP
SequentialBlock mlp = new SequentialBlock(
    Layer.relu(784, 256),
    Layer.relu(256, 128),
    Layer.logSoftmax(128, 10)
);

Tensor output = mlp.forward(input);   // or mlp.apply(input)

// Add layers dynamically
SequentialBlock seq = new SequentialBlock();
seq.add(Layer.linear(64, 32));
seq.add(Layer.relu(32, 10));
```

---

## Activation Functions (`smile.deep.activation`)

All activations implement `ActivationFunction` (which extends `Layer`).
They can be used standalone or placed inside a `SequentialBlock`:

| Class | Activation |
|---|---|
| `ReLU` | max(0, x) |
| `LeakyReLU` | max(αx, x) |
| `GELU` | Gaussian-error linear unit |
| `SiLU` | x·σ(x) (Swish) |
| `Tanh` | tanh(x) |
| `Sigmoid` | σ(x) |
| `Softmax` | softmax along last dim |
| `LogSoftmax` | log-softmax |
| `LogSigmoid` | log(σ(x)) |
| `GLU` | Gated linear unit |
| `HardShrink` | x if |x| > λ else 0 |
| `SoftShrink` | sign(x)·max(0,|x|−λ) |
| `TanhShrink` | x − tanh(x) |

```java
Tensor x = Tensor.randn(8, 16);

ReLU relu = new ReLU(true);          // inplace=true
Tensor y = relu.forward(x);

GELU gelu = new GELU();
Tensor z = gelu.forward(x);
```

---

## Loss Functions (`smile.deep.Loss`)

`Loss` is a `BiFunction<Tensor, Tensor, Tensor>`.  All standard PyTorch losses
are available as static factories:

```java
Loss l1  = Loss.l1();              // MAE
Loss mse  = Loss.mse();             // MSE
Loss bce  = Loss.bce();             // Binary cross-entropy (requires sigmoid input)
Loss bceL = Loss.bceWithLogits();   // BCE + sigmoid (numerically stable)
Loss ce   = Loss.crossEntropy();    // Softmax cross-entropy (standard classification)
Loss nll  = Loss.nll();             // NLL (requires log-softmax input)
Loss sl1  = Loss.smoothL1();        // Huber/smooth-L1 (beta=1)
Loss hub  = Loss.huber(0.5);        // Huber with explicit delta=0.5
Loss kl   = Loss.kl();              // KL divergence
Loss hinge = Loss.hingeEmbedding(); // Hinge embedding

Tensor lossTensor = ce.apply(logits, labels);
double lossVal    = lossTensor.doubleValue();
```

For losses with three arguments:

```java
// Margin ranking and triplet margin
Tensor mrLoss = Loss.marginRanking(input1, input2, target);
Tensor tmLoss = Loss.tripleMarginRanking(anchor, positive, negative);
```

---

## Optimizers (`smile.deep.Optimizer`)

```java
import smile.deep.Optimizer;

Optimizer sgd   = Optimizer.SGD(model, 0.01);
Optimizer adam  = Optimizer.Adam(model, 1e-3);
Optimizer adamW = Optimizer.AdamW(model, 1e-3);
Optimizer rms   = Optimizer.RMSprop(model, 1e-3);

// Per step
optimizer.reset();
loss.backward();
optimizer.step();
```

---

## Model API (`smile.deep.Model`)

Compose a `Model` from a `LayerBlock` to define custom architectures:

```java
LayerBlock net = new LayerBlock("MyCNN") {
    private final Conv2dLayer conv1 = Layer.conv2d(1, 32, 3);
    private final LinearLayer fc = Layer.linear(32 * 13 * 13, 10);

    {
        add("conv1", conv1);
        add("fc", fc);
    }

    @Override
    public Tensor forward(Tensor input) {
        Tensor h = conv1.forward(input);
        h = h.relu_();
        h = Layer.maxPool2d(2).forward(h);
        h = h.view(h.size(0), -1);
        return fc.forward(h);
    }
};

Model model = new Model(net);
```

### Training Loop

```java
Optimizer optimizer = Optimizer.Adam(model, 1e-3);
Loss criterion = Loss.crossEntropy();

model.train(
    10,                        // epochs
    optimizer,
    criterion,
    dataset,                   // training dataset
    testDataset,               // optional validation dataset
    null,                      // optional checkpoint path
    new Accuracy()             // metric(s) to track during validation
);
```

The `Model.train(...)` method handles:
- shuffling via `DataSampler`
- optimizer reset / forward / backward / step
- metric accumulation and logging per epoch

---

## Metrics (`smile.deep.metric`)

All metrics implement `Metric`:

```java
public interface Metric {
    void   update(Tensor output, Tensor target);
    double compute();
    void   reset();
    String name();
}
```

Available metrics:

| Class | Description |
|---|---|
| `Accuracy` | # correct / total |
| `Precision` | TP / (TP + FP) |
| `Recall` | TP / (TP + FN) |
| `F1Score` | Harmonic mean of precision and recall |

For multi-class classification pass an `Averaging` strategy:

```java
Accuracy acc   = new Accuracy();
Precision mp   = new Precision(Averaging.Macro);
Recall   mr    = new Recall(Averaging.Micro);
F1Score  wf1   = new F1Score(Averaging.Weighted);
F1Score  binF1 = new F1Score();   // binary (uses threshold 0.5)

acc.update(output, target);   // call once per batch
double result = acc.compute(); // fraction correct
acc.reset();                   // clear accumulators
```

---

## Data Loading (`smile.deep.Dataset`)

```java
// Create from arrays
float[][] features = ...;
int[]     labels   = ...;
Dataset<SampleBatch> ds = new DatasetImpl(features, labels);

// Iterate batches manually
DataSampler sampler = new DataSampler(ds, batchSize, /*shuffle=*/true);
for (SampleBatch batch : sampler) {
    Tensor x = batch.data();
    Tensor y = batch.target();
    // ... train step
}
```

---

## CUDA Utilities (`smile.deep.CUDA`)

```java
boolean available = CUDA.isAvailable();
int     count     = CUDA.deviceCount();
int     current   = CUDA.currentDevice();
long    free      = CUDA.memoryReserved();     // bytes

boolean bf16 = Tensor.isBF16Supported();       // Ampere or newer
```

---

## End-to-End Examples

### CPU-only MLP Training

```java
import smile.deep.*;
import smile.deep.layer.*;
import smile.deep.tensor.Tensor;

// 1. Build model
SequentialBlock mlp = new SequentialBlock(
    Layer.relu(784, 256),
    Layer.relu(256, 128),
    Layer.logSoftmax(128, 10)
);
Model model = new Model(mlp);

// 2. Optimizer + loss
Optimizer optimizer = Optimizer.Adam(model, 1e-3);
Loss criterion = Loss.nll();

// 3. Training loop
for (int epoch = 0; epoch < 5; epoch++) {
    for (SampleBatch batch : trainSampler) {
        optimizer.reset();
        Tensor logp  = model.forward(batch.data());
        Tensor loss  = criterion.apply(logp, batch.target());
        loss.backward();
        optimizer.step();
    }
}
```

### Training a LeNet on MNIST

```java
LayerBlock net = new LayerBlock("LeNet") {
    private final Conv2dLayer conv1 = Layer.conv2d(1, 6, 5);
    private final Conv2dLayer conv2 = Layer.conv2d(6, 16, 5);
    private final LinearLayer fc1 = Layer.linear(16 * 4 * 4, 120);
    private final LinearLayer fc2 = Layer.linear(120, 84);
    private final LinearLayer fc3 = Layer.linear(84, 10);
    private final MaxPool2dLayer pool = Layer.maxPool2d(2);

    {
        add("conv1", conv1); add("conv2", conv2);
        add("fc1", fc1); add("fc2", fc2); add("fc3", fc3);
    }

    @Override
    public Tensor forward(Tensor input) {
        // [N,1,28,28] → [N,6,12,12]
        Tensor x = pool.forward(new ReLU(true).forward(conv1.forward(input)));
        // → [N,16,4,4]
        x = pool.forward(new ReLU(true).forward(conv2.forward(x)));
        x = x.view(x.size(0), -1);        // flatten
        x = new ReLU(true).forward(fc1.forward(x));
        x = new ReLU(true).forward(fc2.forward(x));
        return new LogSoftmax().forward(fc3.forward(x));
    }
};

// Train on MNIST dataset
Model lenet = new Model(net);
lenet.train(
    10,
    Optimizer.SGD(lenet, 0.01, 0.9, 0.0, 0.0, false),
    Loss.nll(),
    mnistTrainDataset,
    mnistTestDataset,
    null,
    new Accuracy()
);
```

---

*SMILE — Copyright © 2010–2026 Haifeng Li. GNU GPL v3 licensed.*

