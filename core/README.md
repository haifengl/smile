# SMILE Core Module

The `smile-core` module contains core machine learning algorithms and modeling
infrastructure, including manifold learning, anomaly detection, vector
quantization, association rule mining, and ONNX interoperability.

---

## User Guides

- [Anomaly Detection](ANOMALY_DETECTION.md)  
  `smile.anomaly` (`IsolationForest`, one-class `SVM`)

- [Association Rule Mining](ASSOCIATION_RULE_MINING.md)  
  `smile.association` (`FPTree`, `FPGrowth`, `ARM`)

- [Manifold Learning](MANIFOLD.md)  
  `smile.manifold` (`IsoMap`, `LLE`, `LaplacianEigenmap`, `MDS`, `t-SNE`, `UMAP`, `KPCA`)

- [Vector Quantization](VECTOR_QUANTIZATION.md)  
  `smile.vq` (`BIRCH`, `SOM`, `NeuralGas`, `GrowingNeuralGas`, `NeuralMap`)

- [ONNX Guide](ONNX.md)  
  `smile.onnx` inference session, tensor I/O, runtime options and deployment patterns

---

## Notes

- API examples are written for current `smile-core` sources in this repository.
- Prefer these guides for usage patterns and parameter recommendations.
- For package-level implementation details, see `core/src/main/java/smile/*`.

---

*SMILE — Copyright (c) 2010-2026 Haifeng Li. GNU GPL licensed.*

