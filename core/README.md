# SMILE Core Module

The `smile-core` module is the algorithmic heart of SMILE.
It builds on `smile-base` (math, linear algebra, data frames) and provides:

- Supervised learning — classification and regression
- Unsupervised learning — clustering, vector quantization, manifold learning
- Semi-supervised / online methods — sequence labeling, time series
- Feature engineering — scaling, extraction, selection, imputation, SHAP
- Model evaluation — cross-validation, metrics, hyper-parameter optimization
- Production tooling — ONNX inference, model wrappers, anomaly detection

---

## Table of Contents

1. [Module Structure](#module-structure)
2. [Quick Start](#quick-start)
3. [Classification](#classification)
4. [Regression](#regression)
5. [Clustering](#clustering)
6. [Feature Engineering](#feature-engineering)
7. [Anomaly Detection](#anomaly-detection)
8. [Association Rule Mining](#association-rule-mining)
9. [Vector Quantization](#vector-quantization)
10. [Manifold Learning](#manifold-learning)
11. [Sequence Labeling](#sequence-labeling)
12. [Time Series](#time-series)
13. [Model Validation & Metrics](#model-validation--metrics)
14. [Hyper-Parameter Optimization](#hyper-parameter-optimization)
15. [ONNX Inference](#onnx-inference)
16. [User Guides](#user-guides)
17. [Building and Testing](#building-and-testing)

---

## Module Structure

```
smile.anomaly          – Isolation Forest, one-class SVM
smile.association      – FP-Growth, Apriori, ARM
smile.classification   – 20+ classifiers (RF, GBT, SVM, MLP, …)
smile.clustering       – K-Means, DBSCAN, Spectral, GMM, …
smile.feature          – Transforms, extraction, selection, imputation, SHAP
  ├─ smile.feature.transform    – Scaler, Standardizer, Normalizer, …
  ├─ smile.feature.extraction   – PCA, KernelPCA, BagOfWords, encoders, …
  ├─ smile.feature.imputation   – SimpleImputer, KNNImputer, SVDImputer, …
  ├─ smile.feature.selection    – SSR, SNR, FRegression, IV, GAFE
  └─ smile.feature.importance   – SHAP, TreeSHAP
smile.hpo              – Hyper-parameter search (grid, random, Bayesian)
smile.manifold         – IsoMap, LLE, t-SNE, UMAP, KPCA, …
smile.model            – Unified model wrappers (CART, MLP internals)
smile.onnx             – ONNX runtime integration
smile.regression       – 15+ regressors (RF, GBT, SVM, MLP, OLS, LASSO, …)
smile.sequence         – HMM, CRF sequence labeling
smile.timeseries       – ARIMA, GARCH, exponential smoothing, …
smile.validation       – Cross-validation, Bootstrap, metrics
smile.vq               – SOM, Neural Gas, GNG, NeuralMap, BIRCH
```

**Dependency:** `smile-core` → `smile-base` only (no circular deps).

---

## Quick Start

Add the Gradle dependency:

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.github.haifengl:smile-core:6.x.x")
}
```

### Fit and predict a Random Forest in 5 lines

```java
import smile.datasets.Iris;
import smile.classification.RandomForest;
import smile.validation.metric.Accuracy;

var iris = new Iris();
RandomForest rf = RandomForest.fit(iris.formula(), iris.data());
int[] prediction = rf.predict(iris.testData());
System.out.println("Accuracy: " + Accuracy.of(iris.testLabels(), prediction));
```

### Standardize features then train a classifier

```java
import smile.feature.transform.Standardizer;
import smile.data.transform.InvertibleColumnTransform;

InvertibleColumnTransform scaler = Standardizer.fit(trainDf);
DataFrame scaledTrain = scaler.apply(trainDf);
DataFrame scaledTest  = scaler.apply(testDf);

SVM<double[]> svm = SVM.fit(formula, scaledTrain);
```

---

## Classification

`smile.classification` provides over 16 classifiers for binary and multi-class problems.

| Class | Algorithm |
|---|---|
| `RandomForest` | Ensemble of decision trees (bagging + random feature subset) |
| `GradientTreeBoost` | Gradient boosting of decision trees |
| `AdaBoost` | Adaptive boosting |
| `DecisionTree` | Single CART decision tree |
| `LogisticRegression` | L2-regularized logistic regression |
| `SVM` | Support vector machine (one-vs-one multi-class) |
| `MLP` | Multi-layer perceptron |
| `KNN` | k-nearest neighbors |
| `LDA` / `QDA` / `RDA` / `FLD` | Linear/Quadratic/Regularized DA, Fisher's LD |
| `NaiveBayes` / `DiscreteNaiveBayes` | Gaussian and discrete Naive Bayes |
| `RBFNetwork` | Radial basis function network |
| `MaxEntClassifier` | Maximum entropy (log-linear) classifier |

**Common pattern:**

```java
// Formula-based (DataFrame) API
RandomForest rf = RandomForest.fit(Formula.lhs("class"), trainDf);
int label = rf.predict(testTuple);
int[] labels = rf.predict(testDf);
double[] proba = rf.predict(testTuple, new double[k]); // posterior probabilities

// Raw array API
double[][] x = trainDf.toArray();
int[] y = trainDf.column("class").toIntArray();
RandomForest rf2 = RandomForest.fit(x, y);
```

Ensemble models (`RandomForest`, `GradientTreeBoost`) implement `TreeSHAP` for
feature importance. See [Feature Engineering](FEATURE_ENGINEERING.md#treeshap).

📖 **Full guide:** [CLASSIFICATION.md](CLASSIFICATION.md)

---

## Regression

`smile.regression` provides over 13 regressors for continuous-valued prediction.

| Class | Algorithm |
|---|---|
| `RandomForest` | Random forest regression |
| `GradientTreeBoost` | Gradient boosted regression trees |
| `RegressionTree` | Single CART regression tree |
| `OLS` | Ordinary least squares |
| `RidgeRegression` | L2-penalized OLS |
| `LASSO` | L1-penalized OLS (coordinate descent) |
| `ElasticNet` | L1+L2-penalized OLS |
| `SVR` | Support vector regression |
| `MLP` | Multi-layer perceptron regression |
| `GaussianProcessRegression` | Gaussian process with Mercer kernel |
| `RBFNetwork` | Radial basis function network |

**Common pattern:**

```java
OLS ols = OLS.fit(Formula.lhs("price"), trainDf);
double predicted = ols.predict(testTuple);

// Inspect coefficients
System.out.println(ols);
```

📖 **Full guide:** [REGRESSION.md](REGRESSION.md)

---

## Clustering

`smile.clustering` contains algorithms for grouping unlabelled data.

| Class | Algorithm |
|---|---|
| `KMeans` | Lloyd's k-Means (batch) |
| `GMeans` / `XMeans` | Automatic k selection |
| `CLARANS` | Clustering Large Applications |
| `DBScan` | Density-based spatial clustering |
| `DENCLUE` | Density-based with kernel estimation |
| `SpectralClustering` | Graph Laplacian eigenvectors |
| `MEC` | Minimum entropy clustering |
| `SIB` | Sequential information bottleneck |
| `GaussianMixture` | EM-fitted Gaussian mixture model |
| `HierarchicalClustering` | Agglomerative (single/complete/average/Ward) |
| `BIRCH` | Balanced iterative reducing and clustering |

```java
KMeans km = KMeans.fit(data, 5);   // 5 clusters
int[] labels = km.y;               // cluster assignments
double[][] centroids = km.centroids;
int cluster = km.predict(newPoint);
```

📖 **Full guide:** [CLUSTERING.md](CLUSTERING.md)

---

## Feature Engineering

`smile.feature` and its subpackages cover the entire preprocessing pipeline.

### Scaling / Normalization (`smile.feature.transform`)

| Transformer | Output range | Centering | Outlier robust |
|---|---|---|---|
| `Scaler` | [0, 1] | No | No |
| `WinsorScaler` | [0, 1] | No | Yes (percentile) |
| `MaxAbsScaler` | [−1, 1] | No | No |
| `Standardizer` | (−∞, +∞) | Yes | No |
| `RobustStandardizer` | (−∞, +∞) | Yes | Yes (IQR) |
| `Normalizer` | unit norm | No | N/A (row-wise) |

```java
InvertibleColumnTransform t = Standardizer.fit(trainDf);
DataFrame scaled = t.apply(testDf);
DataFrame restored = t.invert(scaled); // lossless roundtrip within training range

// Pipeline
Transform pipeline = Transform.pipeline(
        Standardizer.fit(trainDf),
        MaxAbsScaler.fit(Standardizer.fit(trainDf).apply(trainDf))
);
```

### Dimensionality Reduction (`smile.feature.extraction`)

```java
// Batch PCA (keeps 95% variance by default)
PCA pca = PCA.fit(trainDf);
PCA pca5  = pca.getProjection(5);      // top 5 components
PCA pca90 = pca.getProjection(0.90);   // 90% variance threshold

// Kernel PCA (non-linear)
KernelPCA kpca = KernelPCA.fit(trainDf, new GaussianKernel(1.0), opts);

// Streaming PCA
GHA gha = new GHA(inputDim, 10, TimeFunction.of(0.01));
centeredSamples.forEach(gha::update);

// Random projection (no training needed)
RandomProjection rp = RandomProjection.of(highDim, lowDim);
```

### Feature Selection (`smile.feature.selection`)

```java
// Multi-class: BSS/WSS ratio
SumSquaresRatio[] scores = SumSquaresRatio.fit(df, "label");
Arrays.sort(scores);   // ascending (worst first)

// Binary: signal-to-noise ratio
SignalNoiseRatio[] snr = SignalNoiseRatio.fit(df, "label");

// Regression: univariate F-statistic
FRegression[] fscores = FRegression.fit(df, "target");
String[] significant = Arrays.stream(fscores)
        .filter(r -> r.pvalue() < 0.05).map(FRegression::feature)
        .toArray(String[]::new);

// Genetic algorithm wrapper
GAFE gafe = new GAFE(...);
int[] selected = gafe.apply(generations, population, formula, df, fitness);
```

### Missing Value Imputation (`smile.feature.imputation`)

```java
SimpleImputer imp = SimpleImputer.fit(trainDf);    // mean/mode
KNNImputer knn   = new KNNImputer(trainDf, 5, dist);
double[][] fixed = SVDImputer.impute(rawMatrix, 10, 100);
```

### SHAP Explainability (`smile.feature.importance`)

```java
RandomForest rf = RandomForest.fit(formula, trainDf);
double[] phi = rf.shap(testTuple);   // per-feature contributions
```

📖 **Full guide:** [FEATURE_ENGINEERING.md](FEATURE_ENGINEERING.md)

---

## Anomaly Detection

`smile.anomaly` provides unsupervised outlier and novelty detection.

| Class | Algorithm |
|---|---|
| `IsolationForest` | Random partitioning; anomaly score ∝ isolation path length |
| `SVM` (one-class) | Hypersphere in kernel feature space |

```java
IsolationForest iforest = IsolationForest.fit(trainData, 100); // 100 trees
double[] scores = iforest.score(testData);   // higher = more anomalous
// extensionLevel = 0 → standard IsolationForest
IsolationForest ext = IsolationForest.fit(trainData, 100, extensionLevel);
```

📖 **Full guide:** [ANOMALY_DETECTION.md](ANOMALY_DETECTION.md)

---

## Association Rule Mining

`smile.association` mines frequent itemsets and association rules from
transaction data.

| Class | Algorithm |
|---|---|
| `FPTree` | FP-Tree data structure |
| `FPGrowth` | Frequent pattern growth (FP-Growth) |
| `ARM` | Association rule mining from itemsets |

```java
int[][] transactions = { {1,2,3}, {2,3,4}, {1,3,5}, ... };
List<int[]> itemsets = FPGrowth.apply(transactions, 0.3); // 30% min support

// Mine rules
List<AssociationRule> rules = ARM.apply(itemsets, transactions.length, 0.7);
rules.forEach(r -> System.out.printf("%s => %s  conf=%.2f%n",
        Arrays.toString(r.antecedent()), Arrays.toString(r.consequent()),
        r.confidence()));
```

📖 **Full guide:** [ASSOCIATION_RULE_MINING.md](ASSOCIATION_RULE_MINING.md)

---

## Vector Quantization

`smile.vq` provides competitive-learning algorithms for codebook construction
and topology-preserving maps.

| Class | Algorithm |
|---|---|
| `SOM` | Self-organizing map (batch or online) |
| `NeuralGas` | Neural Gas (batch) |
| `GrowingNeuralGas` | Online growing topology graph |
| `NeuralMap` | Approximate nearest-neighbor map |
| `BIRCH` | CF-tree for large-scale clustering |

```java
SOM som = SOM.fit(data, 10, 10);     // 10×10 hexagonal grid
int unit = som.predict(sample);      // best-matching unit (BMU)

NeuralGas ng = NeuralGas.fit(data, 20);
int centroid = ng.predict(sample);
```

📖 **Full guide:** [VECTOR_QUANTIZATION.md](VECTOR_QUANTIZATION.md)

---

## Manifold Learning

`smile.manifold` provides non-linear dimensionality reduction for visualization
and pre-processing.

| Class | Algorithm |
|---|---|
| `IsoMap` | Geodesic distance + MDS |
| `LLE` | Locally Linear Embedding |
| `LaplacianEigenmap` | Graph Laplacian eigenmaps |
| `UMAP` | Uniform manifold approximation |
| `TSNE` | t-Distributed stochastic neighbor embedding |
| `SammonMapping` | Stress-minimization MDS |
| `KPCA` | Kernel PCA |

```java
double[][] coords2d = UMAP.fit(highDimData).coordinates();
double[][] coords2d = TSNE.fit(highDimData, 2, 30, 200).coordinates();
```

📖 **Full guide:** [MANIFOLD.md](MANIFOLD.md)

---

## Sequence Labeling

`smile.sequence` implements probabilistic models for labeling sequences of
observations.

| Class | Algorithm |
|---|---|
| `HMM` | Hidden Markov Model (Baum–Welch / Viterbi) |
| `CRF` | Conditional Random Field |

```java
// CRF training
CRF<String> crf = CRF.fit(sequences, labels, features, 0.1, 100);
int[] decoded = crf.predict(testSequence);

// HMM Viterbi decoding
HMM hmm = HMM.fit(observationSequences);
int[] hiddenStates = hmm.predict(observations);
```

📖 **Full guide:** [SEQUENCE.md](SEQUENCE.md)

---

## Time Series

`smile.timeseries` covers classical statistical time-series models.

| Class | Algorithm |
|---|---|
| `ARIMA` | AutoRegressive Integrated Moving Average |
| `GARCH` | Generalized Autoregressive Conditional Heteroskedasticity |
| `AR` | Autoregressive model |
| `MA` | Moving average model |
| Utility functions | `acf()`, `pacf()`, `adf()` (ADF unit-root test) |

```java
// Fit ARIMA(1,1,1)
ARIMA model = ARIMA.fit(timeSeries, 1, 1, 1);
double[] forecast = model.forecast(12);   // 12 steps ahead
System.out.println(model);               // AIC, BIC, coefficients

// Stationarity check
double adfStat = TimeSeriesModel.adf(timeSeries, 1);
```

📖 **Full guide:** [TIME_SERIES.md](TIME_SERIES.md)

---

## Model Validation & Metrics

`smile.validation` provides rigorous evaluation protocols.

### Resampling strategies

| Class | Strategy |
|---|---|
| `CrossValidation` | k-fold cross-validation |
| `LOOCV` | Leave-one-out cross-validation |
| `Bootstrap` | Stratified bootstrap |

```java
// 10-fold CV for a classifier
ClassificationMetrics cv = CrossValidation.classification(10, formula, trainDf,
        (f, d) -> RandomForest.fit(f, d));
System.out.println(cv); // accuracy, F1, MCC, …

// Bootstrap for regression
RegressionMetrics boot = Bootstrap.regression(100, x, y,
        (xi, yi) -> OLS.fit(xi, yi));
```

### Classification metrics

`Accuracy`, `Recall`, `Precision`, `F1Score`, `MatthewsCorrelation`,
`AUC`, `LogLoss`, `ConfusionMatrix`, `Sensitivity`, `Specificity`

### Regression metrics

`MAE`, `MSE`, `RMSE`, `RSS`, `R2`, `MeanAbsoluteDeviation`

```java
double acc = Accuracy.of(trueLabels, predictedLabels);
double auc = AUC.of(trueLabels, scores);
ConfusionMatrix cm = ConfusionMatrix.of(trueLabels, predictedLabels);
```

📖 **Full guides:** [VALIDATION.md](VALIDATION.md) · [VALIDATION_METRICS.md](VALIDATION_METRICS.md)

---

## Hyper-Parameter Optimization

`smile.hpo` provides search strategies for tuning model hyper-parameters.

| Strategy | Description |
|---|---|
| Grid search | Exhaustive enumeration of a parameter grid |
| Random search | Random sampling from parameter distributions |
| Bayesian optimization | Surrogate model (GP) + acquisition function |

```java
HPO.Result result = HPO.randomSearch(50, params -> {
    int ntrees   = params.getInt("ntrees");
    int maxDepth = params.getInt("maxDepth");
    RandomForest rf = RandomForest.fit(formula, trainDf,
            new RandomForest.Options(ntrees, maxDepth));
    return CrossValidation.classification(5, formula, trainDf,
            (f, d) -> RandomForest.fit(f, d, new RandomForest.Options(ntrees, maxDepth)))
            .accuracy();
}, Map.of("ntrees",   HPO.range(50, 500),
          "maxDepth", HPO.range(3, 20)));
```

📖 **Full guide:** [HYPER_PARAMETER_OPTIMIZATION.md](HYPER_PARAMETER_OPTIMIZATION.md)

---

## ONNX Inference

`smile.onnx` wraps the ONNX Runtime Java API so you can deploy any
ONNX-compatible model (PyTorch, TensorFlow, scikit-learn, XGBoost, …) inside
a SMILE pipeline.

```java
import smile.onnx.ONNXModel;

try (ONNXModel model = ONNXModel.load("model.onnx")) {
    float[][] input  = prepareInput(df);
    float[][] output = model.predict(input);
}

model.inputNames();    // ["input"]
model.outputNames();   // ["output", "probabilities"]
```

📖 **Full guide:** [ONNX.md](ONNX.md)

---

## User Guides

Detailed documentation for each area of the module:

| Guide | Package(s) covered |
|---|---|
| [CLASSIFICATION.md](CLASSIFICATION.md) | `smile.classification` |
| [REGRESSION.md](REGRESSION.md) | `smile.regression` |
| [CLUSTERING.md](CLUSTERING.md) | `smile.clustering` |
| [FEATURE_ENGINEERING.md](FEATURE_ENGINEERING.md) | `smile.feature.*` |
| [ANOMALY_DETECTION.md](ANOMALY_DETECTION.md) | `smile.anomaly` |
| [ASSOCIATION_RULE_MINING.md](ASSOCIATION_RULE_MINING.md) | `smile.association` |
| [VECTOR_QUANTIZATION.md](VECTOR_QUANTIZATION.md) | `smile.vq` |
| [MANIFOLD.md](MANIFOLD.md) | `smile.manifold` |
| [SEQUENCE.md](SEQUENCE.md) | `smile.sequence` |
| [TIME_SERIES.md](TIME_SERIES.md) | `smile.timeseries` |
| [VALIDATION.md](VALIDATION.md) | `smile.validation` |
| [VALIDATION_METRICS.md](VALIDATION_METRICS.md) | `smile.validation.metric` |
| [HYPER_PARAMETER_OPTIMIZATION.md](HYPER_PARAMETER_OPTIMIZATION.md) | `smile.hpo` |
| [TRAINING.md](TRAINING.md) | Model training utilities and patterns |
| [ONNX.md](ONNX.md) | `smile.onnx` |

---

## Building and Testing

```powershell
# Build only core (skip tests)
./gradlew :core:build -x test

# Run all core tests
./gradlew :core:test

# Run a specific test class
./gradlew :core:test --tests "smile.classification.RandomForestTest"

# Skip integration tests (USPS/MNIST heavy datasets)
./gradlew :core:test -DexcludeTags=integration
```

---

*SMILE — Copyright © 2010–2026 Haifeng Li. GNU GPL licensed.*
