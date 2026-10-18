/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with SMILE Serve. If not, see
 * <https://www.gnu.org/licenses/>.
 */
import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  buildExplainUrl,
  extractShapMetadata,
  formatShapNumber,
  formatSignedShap,
  isKernelShapAlgorithm,
  prepareExplanation,
  rankAndTruncateFeatures,
  shapMethodForAlgorithm,
} from "./shapUtils.js";

describe("shapUtils", () => {
  it("extracts metadata for unsupported model", () => {
    const res = extractShapMetadata({ explanations: { shap: "Not supported" } });
    assert.equal(res.supported, false);
    assert.equal(res.isMultiClass, false);

    const empty = extractShapMetadata(null);
    assert.equal(empty.supported, false);
  });

  it("extracts metadata for 1D regression shap", () => {
    const res = extractShapMetadata({
      prediction: 42.5,
      explanations: { shap: [0.12, -0.45, 0.33] },
    });
    assert.equal(res.supported, true);
    assert.equal(res.isMultiClass, false);
    assert.equal(res.numClasses, 1);
    assert.equal(res.numFeatures, 3);
  });

  it("extracts metadata for 2D multi-class classification shap", () => {
    const res = extractShapMetadata({
      prediction: 0,
      probabilities: [0.96, 0.02, 0.02],
      explanations: {
        shap: [
          [0.1, -0.2, 0.3, -0.4],
          [-0.1, 0.2, -0.3, 0.4],
          [0.0, 0.0, 0.0, 0.0],
        ],
      },
    });
    assert.equal(res.supported, true);
    assert.equal(res.isMultiClass, true);
    assert.equal(res.numClasses, 3);
    assert.equal(res.numFeatures, 4);
  });

  it("prepares explanation for regression and calculates baseValue correctly", () => {
    const featureNames = ["f0", "f1", "f2"];
    const featureValues = { f0: 1.5, f1: 2.0, f2: 3.5 };
    const prediction = {
      prediction: 10.0,
      explanations: { shap: [1.0, 2.0, -0.5] },
    };

    const exp = prepareExplanation({
      prediction,
      featureNames,
      featureValues,
    });

    assert.equal(exp.valid, true);
    assert.equal(exp.predictionValue, 10.0);
    assert.equal(exp.totalAttribution, 2.5);
    // baseValue = 10.0 - 2.5 = 7.5
    assert.equal(exp.baseValue, 7.5);
    assert.equal(exp.features.length, 3);
    assert.equal(exp.features[0].name, "f0");
    assert.equal(exp.features[0].value, 1.5);
    assert.equal(exp.features[0].attribution, 1.0);
    assert.equal(exp.features[2].attribution, -0.5);
    assert.equal(exp.features[2].absAttribution, 0.5);
  });

  it("prepares explanation for multi-class classification with probabilities", () => {
    const featureNames = ["petal_length", "petal_width"];
    const prediction = {
      prediction: 0,
      probabilities: [0.85, 0.15],
      explanations: {
        shap: [
          [0.3, 0.2],
          [-0.3, -0.2],
        ],
      },
    };

    const exp0 = prepareExplanation({
      prediction,
      featureNames,
      targetClass: 0,
    });
    assert.equal(exp0.valid, true);
    assert.equal(exp0.predictionValue, 0.85);
    assert.equal(exp0.totalAttribution, 0.5);
    assert.equal(Math.round(exp0.baseValue * 100) / 100, 0.35);

    const exp1 = prepareExplanation({
      prediction,
      featureNames,
      targetClass: 1,
    });
    assert.equal(exp1.valid, true);
    assert.equal(exp1.predictionValue, 0.15);
    assert.equal(exp1.totalAttribution, -0.5);
    assert.equal(Math.round(exp1.baseValue * 100) / 100, 0.65);
  });

  it("ranks and truncates features into topK and other features", () => {
    const features = [
      { name: "f1", value: 1, attribution: 0.1, absAttribution: 0.1 },
      { name: "f2", value: 2, attribution: -0.5, absAttribution: 0.5 },
      { name: "f3", value: 3, attribution: 0.3, absAttribution: 0.3 },
      { name: "f4", value: 4, attribution: -0.2, absAttribution: 0.2 },
    ];

    const ranked = rankAndTruncateFeatures(features, 2);
    assert.equal(ranked.length, 3); // 2 top + 1 other
    assert.equal(ranked[0].name, "f2"); // -0.5
    assert.equal(ranked[1].name, "f3"); // 0.3
    assert.equal(ranked[2].isOther, true);
    assert.equal(ranked[2].otherCount, 2);
    // sum of f4 (-0.2) + f1 (0.1) = -0.1
    assert.equal(Math.round(ranked[2].attribution * 10) / 10, -0.1);
  });

  it("formats shap numbers and signs", () => {
    assert.equal(formatShapNumber(0.1234), "0.123");
    assert.equal(formatShapNumber(5), "5");
    assert.equal(formatSignedShap(0.1234), "+0.123");
    assert.equal(formatSignedShap(-0.456), "-0.456");
  });

  it("identifies SHAP method for algorithms", () => {
    assert.equal(shapMethodForAlgorithm("random-forest"), "TreeSHAP");
    assert.equal(shapMethodForAlgorithm("gradient-boost"), "TreeSHAP");
    assert.equal(shapMethodForAlgorithm("cart"), "TreeSHAP");
    assert.equal(shapMethodForAlgorithm("ols"), "LinearSHAP");
    assert.equal(shapMethodForAlgorithm("ridge"), "LinearSHAP");
    assert.equal(shapMethodForAlgorithm("logistic"), "KernelSHAP");
    assert.equal(shapMethodForAlgorithm("lda"), "KernelSHAP");
    assert.equal(shapMethodForAlgorithm("mlp"), "KernelSHAP");
    assert.equal(shapMethodForAlgorithm("svm"), "KernelSHAP");
    assert.equal(shapMethodForAlgorithm("gaussian-process"), "KernelSHAP");
    assert.equal(isKernelShapAlgorithm("logistic"), true);
    assert.equal(isKernelShapAlgorithm("random-forest"), false);
    assert.equal(isKernelShapAlgorithm("ols"), false);
  });

  it("builds explain URLs with optional KernelSHAP parameters", () => {
    assert.equal(
      buildExplainUrl("/api/v1/smile/iris_lda-1", false, { samples: 128, ridge: 1e-6 }),
      "/api/v1/smile/iris_lda-1"
    );
    assert.equal(
      buildExplainUrl("/api/v1/smile/iris_lda-1", true, null),
      "/api/v1/smile/iris_lda-1?explain=true"
    );
    assert.equal(
      buildExplainUrl("/api/v1/smile/iris_lda-1", true, { samples: "", ridge: "" }),
      "/api/v1/smile/iris_lda-1?explain=true"
    );
    assert.equal(
      buildExplainUrl("/api/v1/smile/iris_lda-1", true, { samples: "256", ridge: "1e-5" }),
      "/api/v1/smile/iris_lda-1?explain=true&samples=256&ridge=0.00001"
    );
    assert.equal(
      buildExplainUrl("/api/v1/smile/iris_lda-1/stream", true, { samples: 64 }),
      "/api/v1/smile/iris_lda-1/stream?explain=true&samples=64"
    );
  });
});
