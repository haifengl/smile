# SMILE — Deep Learning

The `smile-deep` module wraps the **PyTorch / LibTorch C++ runtime**,
giving Java applications GPU-accelerated deep learning without leaving the JVM.

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
7. [Optimizers (`smile.deep.Optimizer`)](#optimizers-smiledeeoptimizer)
8. [Model API (`smile.deep.Model`)](#model-api-smiledeepmodel)
9. [Metrics (`smile.deep.metric`)](#metrics-smiledeepmetric)
10. [Data Loading (`smile.deep.Dataset`)](#data-loading-smiledeepsdataset)
11. [CUDA Utilities (`smile.deep.CUDA`)](#cuda-utilities-smiledeecuda)
12. [End-to-End Examples](#end-to-end-examples)
    - [Training a LeNet on MNIST](#training-a-lenet-on-mnist)
    - [CPU-only MLP Training](#cpu-only-mlp-training)
13. [Building and Testing](#building-and-testing)

---

## Prerequisites & Dependencies

```kotlin
// build.gradle.kts (consumer module)
dependencies {
    implementation("com.github.haifengl:smile-deep:6.x.x")
}
```

The module ships CPU-only LibTorch by default.  For GPU support, make sure the
native CUDA libraries are on the `java.library.path` and that your Bytedeco
`pytorch` classifier matches your CUDA version (e.g. `linux-x86_64-gpu-cuda12.4`).

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
```

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
    Module asTorch();             // underlying PyTorch Module
    Layer  to(Device device);    // move to GPU
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

var params = model.asTorch().parameters();

Optimizer sgd    = Optimizer.sgd(params, 0.01);            // lr=0.01
Optimizer sgdM   = Optimizer.sgd(params, 0.01, 0.9);       // + momentum
Optimizer adam   = Optimizer.adam(params, 1e-3);
Optimizer adamW  = Optimizer.adamW(params, 1e-3);
Optimizer rms    = Optimizer.rmsprop(params, 1e-3);

// Per step
optimizer.zeroGrad();
loss.backward();
optimizer.step();
```

---

## Model API (`smile.deep.Model`)

Extend `Model` to define custom architectures:

```java
public class MyCNN extends Model {
    private final Conv2dLayer conv1;
    private final LinearLayer fc;

    public MyCNN() {
        this.conv1 = Layer.conv2d(1, 32, 3);
        this.fc    = Layer.linear(32 * 13 * 13, 10);
        register("conv1", conv1);
        register("fc",    fc);
    }

    @Override
    public Tensor forward(Tensor input) {
        Tensor h = conv1.forward(input);
        h = h.relu_();
        h = Layer.maxPool2d(2).forward(h);
        h = h.view(h.size(0), -1);
        return fc.forward(h);
    }
}
```

### Training Loop

```java
MyCNN model = new MyCNN();
Optimizer optimizer = Optimizer.adam(model.asTorch().parameters(), 1e-3);
Loss criterion = Loss.crossEntropy();

model.train(
    10,                        // epochs
    dataset,                   // Dataset<SampleBatch>
    criterion,
    optimizer,
    new Accuracy(),            // metric to track
    testDataset                // optional eval dataset
);
```

The `Model.train(...)` method handles:
- shuffling via `DataSampler`
- zero-grad / forward / backward / step
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

// 2. Optimizer + loss
var params = mlp.asTorch().parameters();
Optimizer optimizer = Optimizer.adam(params, 1e-3);
Loss      criterion = Loss.nll();

// 3. Training loop
for (int epoch = 0; epoch < 5; epoch++) {
    for (SampleBatch batch : trainSampler) {
        optimizer.zeroGrad();
        Tensor logp  = mlp.forward(batch.data());
        Tensor loss  = criterion.apply(logp, batch.target());
        loss.backward();
        optimizer.step();
    }
}
```

### Training a LeNet on MNIST

```java
public class LeNet extends Model {
    private final Conv2dLayer conv1 = Layer.conv2d(1, 6, 5);
    private final Conv2dLayer conv2 = Layer.conv2d(6, 16, 5);
    private final LinearLayer fc1  = Layer.linear(16 * 4 * 4, 120);
    private final LinearLayer fc2  = Layer.linear(120, 84);
    private final LinearLayer fc3  = Layer.linear(84, 10);
    private final MaxPool2dLayer pool = Layer.maxPool2d(2);

    public LeNet() {
        register("conv1", conv1); register("conv2", conv2);
        register("fc1", fc1);   register("fc2", fc2); register("fc3", fc3);
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
}

// Train on MNIST dataset
LeNet lenet = new LeNet();
lenet.train(
    10,
    mnistTrainDataset,
    Loss.nll(),
    Optimizer.sgd(lenet.asTorch().parameters(), 0.01, 0.9),
    new Accuracy(),
    mnistTestDataset
);
```

---

## Building and Testing

```powershell
# Build the module (skip tests)
./gradlew :deep:build -x test

# Compile tests only (fast check)
./gradlew :deep:compileTestJava

# Run all tests (requires LibTorch native library on PATH)
./gradlew :deep:test

# Run a specific test class
./gradlew :deep:test --tests "smile.deep.tensor.TensorTest"
./gradlew :deep:test --tests "smile.deep.metric.MetricTest"
./gradlew :deep:test --tests "smile.deep.LossTest"
./gradlew :deep:test --tests "smile.deep.layer.LayerTest"
```

> **Note:** Tests require the PyTorch / LibTorch native libraries to be present.
> On CI without GPU, the tests run on CPU-only LibTorch.  Tests that require CUDA
> can be tagged `@Tag("gpu")` and excluded with `-DexcludeTags=gpu`.

---

*SMILE — Copyright © 2010–2026 Haifeng Li. GNU GPL v3 licensed.*

