# SMILE — Computer Vision (`smile.vision`)

The `smile.vision` package provides image-classification pipelines built on top
of the `smile.deep` layer stack: image transforms, a folder-per-class image
dataset, the EfficientNet-V2 architecture with pretrained factory methods, and
the ImageNet label tables.

For the shared tensor / layer / optimizer API, see [README.md](README.md).

---

## Table of Contents

1. [Package Map](#package-map)
2. [Image Transforms (`smile.vision.transform`)](#image-transforms-smilevisiontransform)
3. [Image Dataset](#image-dataset)
4. [EfficientNet](#efficientnet)
5. [ImageNet Labels](#imagenet-labels)

---

## Package Map

```
smile.vision
├── transform/     Transform interface, ImageClassification pipeline
├── layer/         Vision-specific blocks: MBConv, FusedMBConv,
│                  Conv2dNormActivation, SqueezeExcitation, StochasticDepth
├── EfficientNet.java   EfficientNet-V2 architecture + pretrained factory methods
├── VisionModel.java    Model subclass coupling a LayerBlock with a Transform
├── ImageDataset.java   Folder-per-class dataset with background prefetch
└── ImageNet.java       1000-class ImageNet label/folder arrays + utilities
```

---

## Image Transforms (`smile.vision.transform`)

`Transform` is a functional interface that converts one or more
`BufferedImage` objects into a 4-D `[N, C, H, W]` float tensor suitable
for a vision model.

```java
import smile.vision.transform.Transform;
import smile.vision.transform.ImageClassification;

// Standard ImageNet preprocessing:
//   resize shorter side → 384, centre-crop to 384×384,
//   normalize with ImageNet mean/std
Transform t = Transform.classification(384, 384);

// Custom crop / resize / normalisation
Transform custom = new ImageClassification(
        /*cropSize=*/  224,
        /*resizeSize=*/ 256,
        /*mean=*/  new float[]{0.5f, 0.5f, 0.5f},
        /*std=*/   new float[]{0.5f, 0.5f, 0.5f},
        /*hints=*/ java.awt.Image.SCALE_SMOOTH
);

// Apply the transform
BufferedImage img = ImageIO.read(new File("cat.jpg"));
try (Tensor batch = t.forward(img)) {
    // batch shape: [1, 3, 384, 384]
}
```

Default ImageNet statistics are available as constants:

```java
float[] mean = Transform.DEFAULT_MEAN;  // {0.485f, 0.456f, 0.406f}
float[] std  = Transform.DEFAULT_STD;   // {0.229f, 0.224f, 0.225f}
```

The `Transform` interface also exposes helper default methods:

```java
// Resize keeping aspect ratio (shorter side → size)
BufferedImage resized = transform.resize(image, 256, Image.SCALE_SMOOTH);

// Centre-crop to square
BufferedImage cropped = transform.crop(resized, 224, false);  // shallow copy
BufferedImage deep    = transform.crop(resized, 224, true);   // deep copy

// Convert image array → float32 [N,C,H,W] tensor (values in [0,1])
Tensor tensor = Transform.toTensor(images);
```

---

## Image Dataset

`ImageDataset` implements `Dataset<SampleBatch>` and reads images from a
**folder-per-class** directory structure:

```
root/
  dog/
    dog001.jpg
    dog002.jpg
  cat/
    cat001.jpg
```

```java
import smile.vision.ImageDataset;
import smile.vision.transform.Transform;

Transform t = Transform.classification(224, 224);

// targetTransform maps a class-folder name to an integer label
ImageDataset ds = new ImageDataset(
        /*batch=*/           32,
        /*root=*/            new File("data/train"),
        /*transform=*/       t,
        /*targetTransform=*/ ImageNet.INSTANCE::targetTransform
);

for (SampleBatch batch : ds) {
    Tensor images = batch.data();    // [32, 3, 224, 224]
    Tensor labels = batch.target();  // [32]
}
```

Image loading runs on a background platform thread and is prefetched into a
bounded queue (capacity 100), so the training loop is never blocked waiting
for I/O.

---

## EfficientNet

`EfficientNet` extends `LayerBlock` and implements the EfficientNet-V2
architecture. Three pretrained `VisionModel` variants are available as
static factory methods:

| Factory | Variant | Input size | Parameters |
|---|---|---|---|
| `EfficientNet.V2S()` | EfficientNet-V2-S | 384 × 384 | ~21 M |
| `EfficientNet.V2M()` | EfficientNet-V2-M | 480 × 480 | ~54 M |
| `EfficientNet.V2L()` | EfficientNet-V2-L | 480 × 480 | ~119 M |

```java
import smile.vision.EfficientNet;

// Load pretrained weights from the default path
VisionModel model = EfficientNet.V2S();

// Or specify a custom checkpoint path
VisionModel model = EfficientNet.V2S("checkpoints/efficientnet_v2_s.pt");

// Run inference on one or more images
BufferedImage img = ImageIO.read(new File("dog.jpg"));
try (Tensor logits = model.forward(img)) {          // shape [1, 1000]
    Tensor probs   = logits.softmax(1);
    int classIdx   = probs.argmax(1, false).intValue();
    System.out.println(ImageNet.INSTANCE.labels()[classIdx]);
}
```

`VisionModel.forward(BufferedImage...)` automatically applies the model's
associated `Transform`, so you never need to preprocess images manually.

The `EfficientNet` constructor accepts an `MBConvConfig[]` array to define a
custom architecture, giving fine-grained control over each inverted-residual
stage:

```java
MBConvConfig[] config = {
    MBConvConfig.FusedMBConv(/*expandRatio=*/1, /*kernel=*/3, /*stride=*/1,
                              /*inCh=*/24, /*outCh=*/24, /*numLayers=*/2),
    MBConvConfig.MBConv(4, 3, 2, 24, 48, 4),
    // ... more stages
};

EfficientNet net = new EfficientNet(
        config,
        /*dropout=*/          0.2,
        /*stochasticDepth=*/  0.2,
        /*numClasses=*/       1000,
        /*lastChannel=*/      1280,
        /*normLayer=*/        null   // defaults to BatchNorm2d
);
```

The vision-specific blocks live in `smile.vision.layer`: `MBConv`,
`FusedMBConv`, `Conv2dNormActivation`, `SqueezeExcitation`, and
`StochasticDepth`.

---

## ImageNet Labels

`ImageNet` is an interface with two 1000-element string arrays and a set of
utility methods for mapping between class indices and human-readable labels:

```java
import smile.vision.ImageNet;

// The single concrete implementation
ImageNet inet = ImageNet.INSTANCE;

// Human-readable label strings ("Egyptian cat", "labrador", …)
String[] labels  = inet.labels();

// Folder names used in the ILSVRC validation set ("n02124075", …)
String[] folders = inet.folders();

// Look up a label by index
String label = inet.labelOf(282);     // e.g. "tiger cat"

// Map a folder name to a label
String name  = inet.classify("n02124075");

// Map a folder name to a class index (useful as targetTransform)
int index    = inet.targetTransform("n02124075");
```

---

*SMILE — Copyright © 2010–2026 Haifeng Li. GNU GPL v3 licensed.*
