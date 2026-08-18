/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
import { IMAGENET_LABELS } from "./imagenetLabels.js";

/** ImageNet-1k classifier head width. */
export const IMAGENET_NUM_CLASSES = 1000;

/**
 * Stable softmax over a numeric vector.
 *
 * @param {number[]} xs
 * @returns {number[]}
 */
export function softmax(xs) {
  let max = -Infinity;
  for (const x of xs) {
    if (Number.isFinite(x) && x > max) {
      max = x;
    }
  }
  if (!Number.isFinite(max)) {
    return xs.map(() => NaN);
  }
  const exps = new Array(xs.length);
  let sum = 0;
  for (let i = 0; i < xs.length; i++) {
    const e = Math.exp(xs[i] - max);
    exps[i] = e;
    sum += e;
  }
  if (sum === 0) {
    return xs.map(() => 0);
  }
  return exps.map((e) => e / sum);
}

/**
 * True when {@code xs} already looks like a probability distribution.
 *
 * @param {number[]} xs
 * @returns {boolean}
 */
export function looksLikeProbabilities(xs) {
  if (!Array.isArray(xs) || xs.length === 0) {
    return false;
  }
  let sum = 0;
  for (const x of xs) {
    if (typeof x !== "number" || !Number.isFinite(x) || x < -1e-6 || x > 1 + 1e-6) {
      return false;
    }
    sum += x;
  }
  return Math.abs(sum - 1) <= 0.05;
}

/**
 * Returns probabilities: pass through if already probability-like, else softmax.
 *
 * @param {number[]} xs
 * @returns {number[]}
 */
export function toProbabilities(xs) {
  return looksLikeProbabilities(xs) ? xs.slice() : softmax(xs);
}

/**
 * Finds the first flat numeric array of length 1000 in an ONNX JSON response.
 *
 * @param {*} predictionJson
 * @returns {{ name: string|null, scores: number[] }|null}
 */
export function findClassifierScores(predictionJson) {
  if (predictionJson == null) {
    return null;
  }
  if (isScoreVector(predictionJson)) {
    return { name: null, scores: predictionJson };
  }
  if (typeof predictionJson !== "object" || Array.isArray(predictionJson)) {
    return null;
  }
  for (const [name, value] of Object.entries(predictionJson)) {
    if (isScoreVector(value)) {
      return { name, scores: value };
    }
  }
  return null;
}

/**
 * @param {*} value
 * @returns {boolean}
 */
function isScoreVector(value) {
  if (!Array.isArray(value) || value.length !== IMAGENET_NUM_CLASSES) {
    return false;
  }
  return value.every((x) => typeof x === "number" && Number.isFinite(x));
}

/**
 * Primary ImageNet label text (text before the first comma).
 *
 * @param {string} label
 * @returns {string}
 */
export function shortLabel(label) {
  if (!label) {
    return "";
  }
  const i = label.indexOf(",");
  return i === -1 ? label : label.slice(0, i).trim();
}

/**
 * Top-k ImageNet classes for a 1000-d score vector.
 *
 * @param {number[]} scores
 * @param {number} [k=5]
 * @returns {Array<{ index: number, label: string, short: string, probability: number }>}
 */
export function imagenetTopK(scores, k = 5) {
  if (!isScoreVector(scores)) {
    return [];
  }
  const probs = toProbabilities(scores);
  const indexed = probs.map((probability, index) => ({ index, probability }));
  indexed.sort((a, b) => b.probability - a.probability);
  const top = indexed.slice(0, Math.max(0, k));
  return top.map(({ index, probability }) => {
    const label = IMAGENET_LABELS[index] ?? `class_${index}`;
    return {
      index,
      label,
      short: shortLabel(label),
      probability,
    };
  });
}

/**
 * Formats top-k entries for a results-table cell.
 *
 * @param {Array<{ short: string, probability: number }>} entries
 * @returns {string}
 */
export function formatTopK(entries) {
  return entries
    .map((e) => `${e.short} (${(e.probability * 100).toFixed(1)}%)`)
    .join("; ");
}

/**
 * Builds table-column values for an ImageNet-style 1000-d classifier output.
 *
 * @param {*} predictionJson ONNX predict JSON body
 * @returns {Record<string, string|number>|null} null when heuristic does not apply
 */
export function imagenetEnrichment(predictionJson) {
  const found = findClassifierScores(predictionJson);
  if (!found) {
    return null;
  }
  const top = imagenetTopK(found.scores, 5);
  if (top.length === 0) {
    return null;
  }
  return {
    top1: top[0].short,
    prob: Math.round(top[0].probability * 1e6) / 1e6,
    top5: formatTopK(top),
  };
}

/**
 * True when model metadata declares a 1000-wide float output (heuristic hint).
 *
 * @param {{ outputs?: Array<{ shape?: number[] }> }|null|undefined} info
 * @returns {boolean}
 */
export function modelLooksLikeImageNet(info) {
  const outputs = info?.outputs;
  if (!Array.isArray(outputs)) {
    return false;
  }
  for (const out of outputs) {
    const shape = out?.shape;
    if (!Array.isArray(shape) || shape.length === 0) {
      continue;
    }
    const last = shape[shape.length - 1];
    if (last === IMAGENET_NUM_CLASSES) {
      return true;
    }
    // e.g. [1, 1000] or [1000]
    if (shape.length === 1 && shape[0] === IMAGENET_NUM_CLASSES) {
      return true;
    }
  }
  return false;
}
