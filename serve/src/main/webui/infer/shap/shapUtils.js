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

const TREE_SHAP_ALGORITHMS = new Set([
  "random-forest",
  "gradient-boost",
  "ada-boost",
  "cart",
]);

const LINEAR_SHAP_ALGORITHMS = new Set([
  "ols",
  "lasso",
  "elastic-net",
  "ridge",
]);

const CLUSTERING_ALGORITHMS = new Set([
  "kmeans",
  "xmeans",
  "gmeans",
  "deterministic-annealing",
  "da",
]);

/**
 * Returns the SHAP method name ("TreeSHAP", "LinearSHAP", or "KernelSHAP")
 * for a SMILE model algorithm name, or {@code null} for clustering models.
 *
 * @param {string|null|undefined} algorithm
 * @returns {"TreeSHAP"|"LinearSHAP"|"KernelSHAP"|null}
 */
export function shapMethodForAlgorithm(algorithm) {
  if (!algorithm || typeof algorithm !== "string") {
    return "SHAP";
  }
  const normalized = algorithm.trim().toLowerCase();
  if (CLUSTERING_ALGORITHMS.has(normalized)) {
    return null;
  }
  if (TREE_SHAP_ALGORITHMS.has(normalized)) {
    return "TreeSHAP";
  }
  if (LINEAR_SHAP_ALGORITHMS.has(normalized)) {
    return "LinearSHAP";
  }
  return "KernelSHAP";
}

/**
 * Returns true if the algorithm uses KernelSHAP (and therefore accepts
 * optional coalition sample count and ridge regularization parameters).
 *
 * @param {string|null|undefined} algorithm
 * @returns {boolean}
 */
export function isKernelShapAlgorithm(algorithm) {
  return shapMethodForAlgorithm(algorithm) === "KernelSHAP";
}

/**
 * Builds an inference or streaming URL with optional explain and KernelSHAP parameters.
 *
 * @param {string} baseUrl
 * @param {boolean} explain
 * @param {{ samples?: string|number, ridge?: string|number }|null} [shapOptions]
 * @returns {string}
 */
export function buildExplainUrl(baseUrl, explain, shapOptions = null) {
  if (!explain) {
    return baseUrl;
  }
  const params = new URLSearchParams();
  params.set("explain", "true");
  if (shapOptions && typeof shapOptions === "object") {
    const rawSamples = shapOptions.samples;
    if (rawSamples != null && String(rawSamples).trim() !== "") {
      const parsedSamples = Number(rawSamples);
      if (Number.isInteger(parsedSamples) && parsedSamples >= 0) {
        params.set("samples", String(parsedSamples));
      }
    }
    const rawRidge = shapOptions.ridge;
    if (rawRidge != null && String(rawRidge).trim() !== "") {
      const parsedRidge = Number(rawRidge);
      if (Number.isFinite(parsedRidge) && parsedRidge >= 0) {
        params.set("ridge", String(parsedRidge));
      }
    }
  }
  return `${baseUrl}?${params.toString()}`;
}

/**
 * Normalizes SHAP explanations from server prediction responses.
 *
 * Server TreeSHAP returns:
 * - Classification: shap is 2D float array [classes][features]
 * - Regression: shap is 1D float array [features]
 * - Unsupported: shap is "Not supported" or null
 *
 * @param {object} prediction the raw prediction object or normalized row
 * @returns {{
 *   supported: boolean,
 *   isMultiClass: boolean,
 *   numClasses: number,
 *   numFeatures: number,
 *   rawShap: number[]|number[][]|null
 * }}
 */
export function extractShapMetadata(prediction) {
  const explanations = prediction?.explanations || prediction?.values?.explanations;
  const rawShap = explanations?.shap;

  if (!rawShap || rawShap === "Not supported") {
    return {
      supported: false,
      isMultiClass: false,
      numClasses: 0,
      numFeatures: 0,
      rawShap: null,
    };
  }

  if (Array.isArray(rawShap) && Array.isArray(rawShap[0])) {
    // 2D multi-class classification
    const numClasses = rawShap.length;
    const numFeatures = rawShap[0]?.length || 0;
    return {
      supported: numFeatures > 0,
      isMultiClass: true,
      numClasses,
      numFeatures,
      rawShap,
    };
  }

  if (Array.isArray(rawShap)) {
    // 1D regression
    return {
      supported: rawShap.length > 0,
      isMultiClass: false,
      numClasses: 1,
      numFeatures: rawShap.length,
      rawShap,
    };
  }

  return {
    supported: false,
    isMultiClass: false,
    numClasses: 0,
    numFeatures: 0,
    rawShap: null,
  };
}

/**
 * Prepares an explanation record for a chosen target class (or regression).
 * Computes baseline E[f(x)] using the additive efficiency axiom:
 *   E[f(x)] = f(x) - sum(phi_j)
 *
 * @param {object} params
 * @param {object} params.prediction raw or row prediction object
 * @param {string[]} params.featureNames array of feature names in schema order
 * @param {Record<string, *>|null} [params.featureValues] input feature values mapping
 * @param {number} [params.targetClass=0] class index for classification
 * @returns {{
 *   valid: boolean,
 *   predictionValue: number,
 *   baseValue: number,
 *   features: Array<{
 *     name: string,
 *     value: *,
 *     attribution: number,
 *     absAttribution: number
 *   }>,
 *   totalAttribution: number
 * }}
 */
export function prepareExplanation({
  prediction,
  featureNames = [],
  featureValues = null,
  targetClass = 0,
}) {
  const meta = extractShapMetadata(prediction);
  if (!meta.supported) {
    return {
      valid: false,
      predictionValue: 0,
      baseValue: 0,
      features: [],
      totalAttribution: 0,
    };
  }

  let attributions = [];
  let predValue = 0;

  if (meta.isMultiClass) {
    const classIdx = Math.max(0, Math.min(meta.numClasses - 1, targetClass));
    attributions = meta.rawShap[classIdx] || [];

    // Target prediction value: class probability if available, otherwise 1 or 0
    const probs = prediction?.probabilities || prediction?.values?.probabilities;
    if (Array.isArray(probs) && typeof probs[classIdx] === "number") {
      predValue = probs[classIdx];
    } else {
      const pred = prediction?.prediction ?? prediction?.values?.prediction;
      predValue = Number(pred) === classIdx ? 1.0 : 0.0;
    }
  } else {
    // Regression
    attributions = meta.rawShap;
    const pred = prediction?.prediction ?? prediction?.values?.prediction;
    predValue = typeof pred === "number" ? pred : Number(pred) || 0;
  }

  let totalAttribution = 0;
  for (let i = 0; i < attributions.length; i++) {
    totalAttribution += Number(attributions[i]) || 0;
  }

  const baseValue = predValue - totalAttribution;

  const inputs = featureValues || prediction?.input || prediction?.values?.input || {};

  const features = attributions.map((phi, idx) => {
    const name = featureNames[idx] || `feature_${idx}`;
    const value = inputs[name] ?? null;
    const attribution = Number(phi) || 0;
    return {
      name,
      value,
      attribution,
      absAttribution: Math.abs(attribution),
    };
  });

  return {
    valid: true,
    predictionValue: predValue,
    baseValue,
    features,
    totalAttribution,
  };
}

/**
 * Truncates and sorts features by absolute attribution magnitude for Waterfall/Force plots.
 * If features.length > topK, bundles the remaining features into "+ N other features".
 *
 * @param {Array<{ name: string, value: *, attribution: number, absAttribution: number }>} features
 * @param {number} [topK=10]
 * @returns {Array<{ name: string, value: *, attribution: number, isOther?: boolean, otherCount?: number }>}
 */
export function rankAndTruncateFeatures(features, topK = 10) {
  if (!Array.isArray(features)) return [];
  const sorted = [...features].sort((a, b) => b.absAttribution - a.absAttribution);

  if (sorted.length <= topK) {
    return sorted;
  }

  const top = sorted.slice(0, topK);
  const remaining = sorted.slice(topK);
  const remainingSum = remaining.reduce((sum, f) => sum + f.attribution, 0);

  top.push({
    name: `+ ${remaining.length} other features`,
    value: null,
    attribution: remainingSum,
    absAttribution: Math.abs(remainingSum),
    isOther: true,
    otherCount: remaining.length,
  });

  return top;
}

/**
 * Formats a numeric value nicely for plot labels.
 *
 * @param {number} num
 * @param {number} [digits=3]
 * @returns {string}
 */
export function formatShapNumber(num, digits = 3) {
  if (num == null || Number.isNaN(num)) return "";
  if (Math.abs(num) < 1e-6 && num !== 0) return num.toExponential(digits);
  return Number(num.toFixed(digits)).toString();
}

/**
 * Formats attribution with an explicit sign (+ or -).
 *
 * @param {number} num
 * @param {number} [digits=3]
 * @returns {string}
 */
export function formatSignedShap(num, digits = 3) {
  if (num == null || Number.isNaN(num)) return "";
  const sign = num > 0 ? "+" : "";
  return `${sign}${formatShapNumber(num, digits)}`;
}
