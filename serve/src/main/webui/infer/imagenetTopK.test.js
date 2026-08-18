/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * Run with: node --test serve/src/main/webui/infer/imagenetTopK.test.js
 * (from the repository root, or with a relative path from this directory)
 */
import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { IMAGENET_LABELS } from "./imagenetLabels.js";
import {
  findClassifierScores,
  formatTopK,
  imagenetEnrichment,
  imagenetTopK,
  IMAGENET_NUM_CLASSES,
  looksLikeProbabilities,
  modelLooksLikeImageNet,
  shortLabel,
  softmax,
  toProbabilities,
} from "./imagenetTopK.js";

describe("imagenetTopK helpers", () => {
  it("ships 1000 ImageNet labels", () => {
    assert.equal(IMAGENET_LABELS.length, IMAGENET_NUM_CLASSES);
    assert.equal(IMAGENET_LABELS[0], "tench, Tinca tinca");
    assert.equal(IMAGENET_LABELS[515], "cowboy hat, ten-gallon hat");
  });

  it("shortLabel keeps text before the first comma", () => {
    assert.equal(shortLabel("cowboy hat, ten-gallon hat"), "cowboy hat");
    assert.equal(shortLabel("stingray"), "stingray");
  });

  it("softmax is normalized and peaks at the max logit", () => {
    const p = softmax([1, 3, 1]);
    const sum = p.reduce((a, b) => a + b, 0);
    assert.ok(Math.abs(sum - 1) < 1e-9);
    assert.equal(p.indexOf(Math.max(...p)), 1);
  });

  it("looksLikeProbabilities accepts near-one-hot vectors", () => {
    const oneHot = new Array(IMAGENET_NUM_CLASSES).fill(0);
    oneHot[515] = 1;
    assert.equal(looksLikeProbabilities(oneHot), true);
    assert.equal(looksLikeProbabilities([0.2, 0.3, 10]), false);
  });

  it("toProbabilities passes through probability vectors", () => {
    const oneHot = new Array(IMAGENET_NUM_CLASSES).fill(0);
    oneHot[10] = 1;
    const p = toProbabilities(oneHot);
    assert.equal(p[10], 1);
  });

  it("imagenetTopK ranks a peaked vector as the expected class", () => {
    const scores = new Array(IMAGENET_NUM_CLASSES).fill(0);
    scores[515] = 5;
    scores[0] = 1;
    scores[1] = 2;
    const top = imagenetTopK(scores, 3);
    assert.equal(top.length, 3);
    assert.equal(top[0].index, 515);
    assert.equal(top[0].short, "cowboy hat");
    assert.ok(top[0].probability > top[1].probability);
  });

  it("findClassifierScores finds a 1000-d output array", () => {
    const scores = new Array(IMAGENET_NUM_CLASSES).fill(0.001);
    scores[0] = 1;
    const found = findClassifierScores({ logits: scores, other: [1, 2] });
    assert.equal(found.name, "logits");
    assert.equal(found.scores.length, IMAGENET_NUM_CLASSES);
    assert.equal(findClassifierScores({ x: [1, 2, 3] }), null);
  });

  it("imagenetEnrichment builds top1/prob/top5 columns", () => {
    const scores = new Array(IMAGENET_NUM_CLASSES).fill(0);
    scores[515] = 10;
    const row = imagenetEnrichment({ output: scores });
    assert.equal(row.top1, "cowboy hat");
    assert.ok(row.prob > 0.9);
    assert.match(row.top5, /cowboy hat/);
    assert.equal(formatTopK(imagenetTopK(scores, 5)), row.top5);
  });

  it("modelLooksLikeImageNet inspects output shapes", () => {
    assert.equal(
      modelLooksLikeImageNet({ outputs: [{ shape: [1, 1000] }] }),
      true
    );
    assert.equal(
      modelLooksLikeImageNet({ outputs: [{ shape: [1, 10] }] }),
      false
    );
  });
});
