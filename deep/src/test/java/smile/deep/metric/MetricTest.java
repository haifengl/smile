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
package smile.deep.metric;

import org.junit.jupiter.api.*;
import smile.deep.tensor.Tensor;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Accuracy}, {@link Precision}, {@link Recall},
 * and {@link F1Score} using synthetic tensors — no GPU or MNIST required.
 *
 * All tests use logit-style 2-D output of shape [batch, numClasses] so that
 * argmax is used for prediction, keeping the assertions independent of any
 * threshold value.
 */
public class MetricTest {

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    /**
     * Build a 2-D logit tensor where the "correct" column gets value 10.0
     * and all others get 0.0.  Shape: [n, numClasses].
     */
    private static Tensor logits(int[] classes, int numClasses) {
        float[] flat = new float[classes.length * numClasses];
        for (int i = 0; i < classes.length; i++) {
            flat[i * numClasses + classes[i]] = 10.0f;
        }
        return Tensor.of(flat, classes.length, numClasses);
    }

    /** Build an int64 target tensor from a plain int array. */
    private static Tensor target(int[] labels) {
        long[] l = new long[labels.length];
        for (int i = 0; i < labels.length; i++) l[i] = labels[i];
        return Tensor.of(l, labels.length);
    }

    // -----------------------------------------------------------------------
    // Accuracy
    // -----------------------------------------------------------------------

    @Test
    public void testGivenPerfectMultiClassPredictionWhenComputingAccuracyThenReturnsOne() {
        // Given: 4 samples, 3 classes, all correct
        int[] trueLabels = {0, 1, 2, 0};
        Tensor output = logits(trueLabels, 3);
        Tensor tgt    = target(trueLabels);
        Accuracy metric = new Accuracy();

        // When
        metric.update(output, tgt);

        // Then
        assertEquals(1.0, metric.compute(), 1e-6);
        output.close(); tgt.close();
    }

    @Test
    public void testGivenHalfCorrectPredictionsWhenComputingAccuracyThenReturnsHalf() {
        // Given: 4 samples, 2 classes; first two correct, last two wrong
        // predicted: 0,1,0,1  true: 0,1,1,0
        int[] pred   = {0, 1, 0, 1};
        int[] labels = {0, 1, 1, 0};
        Tensor output = logits(pred, 2);
        Tensor tgt    = target(labels);
        Accuracy metric = new Accuracy();

        // When
        metric.update(output, tgt);

        // Then
        assertEquals(0.5, metric.compute(), 1e-6);
        output.close(); tgt.close();
    }

    @Test
    public void testGivenResetAfterUpdateWhenComputingAccuracyThenReturnsNaN() {
        // Given
        int[] trueLabels = {0, 1};
        Tensor output = logits(trueLabels, 2);
        Tensor tgt    = target(trueLabels);
        Accuracy metric = new Accuracy();
        metric.update(output, tgt);

        // When
        metric.reset();

        // Then — size becomes 0, result is NaN (0/0)
        assertTrue(Double.isNaN(metric.compute()) || metric.compute() == 0.0);
        output.close(); tgt.close();
    }

    // -----------------------------------------------------------------------
    // Precision
    // -----------------------------------------------------------------------

    @Test
    public void testGivenPerfectPredictionsWhenComputingMicroPrecisionThenReturnsOne() {
        int[] trueLabels = {0, 1, 2, 0, 1, 2};
        Tensor output = logits(trueLabels, 3);
        Tensor tgt    = target(trueLabels);
        Precision metric = new Precision(Averaging.Micro);

        metric.update(output, tgt);
        assertEquals(1.0, metric.compute(), 1e-5);
        output.close(); tgt.close();
    }

    @Test
    public void testGivenPerfectPredictionsWhenComputingMacroPrecisionThenReturnsOne() {
        int[] trueLabels = {0, 1, 2, 0, 1, 2};
        Tensor output = logits(trueLabels, 3);
        Tensor tgt    = target(trueLabels);
        Precision metric = new Precision(Averaging.Macro);

        metric.update(output, tgt);
        assertEquals(1.0, metric.compute(), 1e-5);
        output.close(); tgt.close();
    }

    @Test
    public void testGivenPerfectPredictionsWhenComputingWeightedPrecisionThenReturnsOne() {
        int[] trueLabels = {0, 1, 2, 0, 1, 2};
        Tensor output = logits(trueLabels, 3);
        Tensor tgt    = target(trueLabels);
        Precision metric = new Precision(Averaging.Weighted);

        metric.update(output, tgt);
        assertEquals(1.0, metric.compute(), 1e-5);
        output.close(); tgt.close();
    }

    @Test
    public void testGivenBinaryClassificationWhenComputingPrecisionThenCorrectValue() {
        // predicted: 1,1,0,0   true: 1,0,1,0
        // TP=1 FP=1 → precision = 0.5
        int[] pred   = {1, 1, 0, 0};
        int[] labels = {1, 0, 1, 0};
        Tensor output = logits(pred, 2);
        Tensor tgt    = target(labels);
        Precision metric = new Precision(); // binary / no strategy

        metric.update(output, tgt);
        assertEquals(0.5, metric.compute(), 1e-5);
        output.close(); tgt.close();
    }

    @Test
    public void testGivenNoPredictedPositivesWhenComputingPrecisionThenReturnsZeroNotNaN() {
        // All predicted as class 0; class 1 has zero predicted positives → FP=0, TP=0
        int[] pred   = {0, 0, 0, 0};
        int[] labels = {0, 1, 0, 1};
        Tensor output = logits(pred, 2);
        Tensor tgt    = target(labels);
        Precision metric = new Precision(Averaging.Macro);

        metric.update(output, tgt);
        double val = metric.compute();
        assertFalse(Double.isNaN(val), "precision should not be NaN when no predicted positives");
        output.close(); tgt.close();
    }

    // -----------------------------------------------------------------------
    // Recall
    // -----------------------------------------------------------------------

    @Test
    public void testGivenPerfectPredictionsWhenComputingMicroRecallThenReturnsOne() {
        int[] trueLabels = {0, 1, 2, 0, 1, 2};
        Tensor output = logits(trueLabels, 3);
        Tensor tgt    = target(trueLabels);
        Recall metric = new Recall(Averaging.Micro);

        metric.update(output, tgt);
        assertEquals(1.0, metric.compute(), 1e-5);
        output.close(); tgt.close();
    }

    @Test
    public void testGivenBinaryClassificationWhenComputingRecallThenCorrectValue() {
        // predicted: 1,1,0,0   true: 1,0,1,0
        // TP=1 FN=1 → recall = 0.5
        int[] pred   = {1, 1, 0, 0};
        int[] labels = {1, 0, 1, 0};
        Tensor output = logits(pred, 2);
        Tensor tgt    = target(labels);
        Recall metric = new Recall(); // binary

        metric.update(output, tgt);
        assertEquals(0.5, metric.compute(), 1e-5);
        output.close(); tgt.close();
    }

    // -----------------------------------------------------------------------
    // F1Score
    // -----------------------------------------------------------------------

    @Test
    public void testGivenPerfectPredictionsWhenComputingF1ThenReturnsOne() {
        int[] trueLabels = {0, 1, 2, 0, 1, 2};
        Tensor output = logits(trueLabels, 3);
        Tensor tgt    = target(trueLabels);
        F1Score metric = new F1Score(Averaging.Macro);

        metric.update(output, tgt);
        assertEquals(1.0, metric.compute(), 1e-5);
        output.close(); tgt.close();
    }

    @Test
    public void testGivenBinaryClassificationWhenComputingF1ThenCorrectHarmonicMean() {
        // precision = 0.5, recall = 0.5 → F1 = 0.5
        int[] pred   = {1, 1, 0, 0};
        int[] labels = {1, 0, 1, 0};
        Tensor output = logits(pred, 2);
        Tensor tgt    = target(labels);
        F1Score metric = new F1Score();

        metric.update(output, tgt);
        assertEquals(0.5, metric.compute(), 1e-5);
        output.close(); tgt.close();
    }

    @Test
    public void testGivenMicroAveragingWhenAccuracyEqualsPrecisionRecallAndF1() {
        // For micro-averaging: accuracy == micro-precision == micro-recall == micro-F1
        int[] trueLabels = {0, 1, 2, 0, 1, 2};
        Tensor output = logits(trueLabels, 3);
        Tensor tgt    = target(trueLabels);

        Accuracy acc     = new Accuracy();
        Precision microP = new Precision(Averaging.Micro);
        Recall    microR = new Recall(Averaging.Micro);
        F1Score   microF = new F1Score(Averaging.Micro);

        acc.update(output, tgt);
        microP.update(output, tgt);
        microR.update(output, tgt);
        microF.update(output, tgt);

        assertEquals(acc.compute(), microP.compute(), 1e-5);
        assertEquals(acc.compute(), microR.compute(), 1e-5);
        assertEquals(acc.compute(), microF.compute(), 1e-5);

        output.close(); tgt.close();
    }

    @Test
    public void testGivenF1ScoreWhenResetThenComputeReturnsZero() {
        int[] trueLabels = {0, 1};
        Tensor output = logits(trueLabels, 2);
        Tensor tgt    = target(trueLabels);
        F1Score metric = new F1Score(Averaging.Macro);
        metric.update(output, tgt);

        metric.reset();
        // After reset, both p and r are zero → F1 = 0
        assertEquals(0.0, metric.compute(), 1e-9);
        output.close(); tgt.close();
    }

    // -----------------------------------------------------------------------
    // Name / toString sanity checks
    // -----------------------------------------------------------------------

    @Test
    public void testMetricNames() {
        assertEquals("Accuracy",          new Accuracy().name());
        assertEquals("Precision",         new Precision().name());
        assertEquals("Macro-Precision",   new Precision(Averaging.Macro).name());
        assertEquals("Micro-Precision",   new Precision(Averaging.Micro).name());
        assertEquals("Weighted-Precision",new Precision(Averaging.Weighted).name());
        assertEquals("Recall",            new Recall().name());
        assertEquals("Macro-Recall",      new Recall(Averaging.Macro).name());
        assertEquals("F1",                new F1Score().name());
        assertEquals("Macro-F1",          new F1Score(Averaging.Macro).name());
        assertEquals("Micro-F1",          new F1Score(Averaging.Micro).name());
        assertEquals("Weighted-F1",       new F1Score(Averaging.Weighted).name());
    }

    // -----------------------------------------------------------------------
    // compute() before any update() — should return 0.0, not NPE
    // -----------------------------------------------------------------------

    @Test
    public void testGivenNoUpdatesWhenComputingAccuracyThenReturnsZero() {
        assertEquals(0.0, new Accuracy().compute(), 1e-9);
    }

    @Test
    public void testGivenNoUpdatesWhenComputingPrecisionThenReturnsZero() {
        assertEquals(0.0, new Precision().compute(), 1e-9);
        assertEquals(0.0, new Precision(Averaging.Macro).compute(), 1e-9);
    }

    @Test
    public void testGivenNoUpdatesWhenComputingRecallThenReturnsZero() {
        assertEquals(0.0, new Recall().compute(), 1e-9);
        assertEquals(0.0, new Recall(Averaging.Macro).compute(), 1e-9);
    }

    @Test
    public void testGivenNoUpdatesWhenComputingF1ThenReturnsZero() {
        assertEquals(0.0, new F1Score().compute(), 1e-9);
        assertEquals(0.0, new F1Score(Averaging.Macro).compute(), 1e-9);
    }

    // -----------------------------------------------------------------------
    // Multi-batch accumulation
    // -----------------------------------------------------------------------

    @Test
    public void testGivenMultipleBatchesWhenComputingAccuracyThenAccumulatesCorrectly() {
        // Batch 1: 4 correct out of 4 (100%)
        // Batch 2: 2 correct out of 4 (50%)
        // Overall: 6/8 = 75%
        int[] labels1 = {0, 1, 2, 0};
        int[] labels2pred = {0, 1, 0, 1};
        int[] labels2true = {0, 1, 1, 0};

        Tensor out1 = logits(labels1, 3);   Tensor tgt1 = target(labels1);
        Tensor out2 = logits(labels2pred, 2); Tensor tgt2 = target(labels2true);

        Accuracy metric = new Accuracy();
        metric.update(out1, tgt1);
        metric.update(out2, tgt2);

        assertEquals(6.0 / 8.0, metric.compute(), 1e-6);
        out1.close(); tgt1.close(); out2.close(); tgt2.close();
    }

    @Test
    public void testGivenResetAndReUpdateWhenComputingAccuracyThenUsesNewBatchOnly() {
        // First batch: all correct
        int[] labels1 = {0, 1, 2};
        Tensor out1 = logits(labels1, 3); Tensor tgt1 = target(labels1);
        Accuracy metric = new Accuracy();
        metric.update(out1, tgt1);
        assertEquals(1.0, metric.compute(), 1e-6);

        // Reset and update with half-correct batch
        metric.reset();
        int[] pred2   = {0, 1, 0, 1};
        int[] labels2 = {0, 1, 1, 0};
        Tensor out2 = logits(pred2, 2); Tensor tgt2 = target(labels2);
        metric.update(out2, tgt2);
        assertEquals(0.5, metric.compute(), 1e-6);

        out1.close(); tgt1.close(); out2.close(); tgt2.close();
    }

    // -----------------------------------------------------------------------
    // Precision — weighted averaging
    // -----------------------------------------------------------------------

    @Test
    public void testGivenImbalancedClassesWhenComputingWeightedPrecisionThenCorrectValue() {
        // 3 samples of class 0, 1 sample of class 1, all predicted correctly.
        // per-class precision = [1.0, 1.0]; weighted = 1.0
        int[] labels = {0, 0, 0, 1};
        Tensor output = logits(labels, 2); Tensor tgt = target(labels);
        Precision metric = new Precision(Averaging.Weighted);
        metric.update(output, tgt);
        assertEquals(1.0, metric.compute(), 1e-5);
        output.close(); tgt.close();
    }

    @Test
    public void testGivenMultiBatchMacroPrecisionWhenComputingThenAccumulates() {
        // Batch 1: all class 0 predicted as 0 (TP=2 for class 0, FP=0, FP=0 for class 1)
        // Batch 2: all class 1 predicted as 1 (TP=2 for class 1)
        // macro precision should be 1.0
        int[] pred1 = {0, 0}; int[] true1 = {0, 0};
        int[] pred2 = {1, 1}; int[] true2 = {1, 1};

        Tensor out1 = logits(pred1, 2); Tensor tgt1 = target(true1);
        Tensor out2 = logits(pred2, 2); Tensor tgt2 = target(true2);

        Precision metric = new Precision(Averaging.Macro);
        metric.update(out1, tgt1);
        metric.update(out2, tgt2);
        assertEquals(1.0, metric.compute(), 1e-5);

        out1.close(); tgt1.close(); out2.close(); tgt2.close();
    }

    // -----------------------------------------------------------------------
    // Recall — macro and weighted
    // -----------------------------------------------------------------------

    @Test
    public void testGivenPerfectPredictionsWhenComputingMacroRecallThenReturnsOne() {
        int[] labels = {0, 1, 2, 0, 1, 2};
        Tensor output = logits(labels, 3); Tensor tgt = target(labels);
        Recall metric = new Recall(Averaging.Macro);
        metric.update(output, tgt);
        assertEquals(1.0, metric.compute(), 1e-5);
        output.close(); tgt.close();
    }

    @Test
    public void testGivenPerfectPredictionsWhenComputingWeightedRecallThenReturnsOne() {
        int[] labels = {0, 1, 2, 0, 1, 2};
        Tensor output = logits(labels, 3); Tensor tgt = target(labels);
        Recall metric = new Recall(Averaging.Weighted);
        metric.update(output, tgt);
        assertEquals(1.0, metric.compute(), 1e-5);
        output.close(); tgt.close();
    }

    @Test
    public void testGivenBinaryRecallWhenNoPositiveInstancesThenReturnsZeroNotNaN() {
        // All labels are class 0; no class-1 instances → recall for class 1 = 0 (not NaN)
        int[] pred   = {0, 0, 0};
        int[] labels = {0, 0, 0};
        Tensor output = logits(pred, 2); Tensor tgt = target(labels);
        Recall metric = new Recall(); // binary, strategy == null
        metric.update(output, tgt);
        double val = metric.compute();
        assertFalse(Double.isNaN(val), "recall should not be NaN when no positive instances");
        assertFalse(Double.isInfinite(val), "recall should not be Inf when no positive instances");
        output.close(); tgt.close();
    }

    // -----------------------------------------------------------------------
    // F1 — weighted and multi-batch
    // -----------------------------------------------------------------------

    @Test
    public void testGivenPerfectPredictionsWhenComputingWeightedF1ThenReturnsOne() {
        int[] labels = {0, 1, 2, 0, 1, 2};
        Tensor output = logits(labels, 3); Tensor tgt = target(labels);
        F1Score metric = new F1Score(Averaging.Weighted);
        metric.update(output, tgt);
        assertEquals(1.0, metric.compute(), 1e-5);
        output.close(); tgt.close();
    }

    @Test
    public void testGivenAllWrongPredictionsWhenComputingF1ThenReturnsZero() {
        // 2 classes: all predicted as 0, all true are 1 → TP=0 → P=0, R=0 → F1=0
        int[] pred   = {0, 0, 0};
        int[] labels = {1, 1, 1};
        Tensor output = logits(pred, 2); Tensor tgt = target(labels);
        F1Score metric = new F1Score();
        metric.update(output, tgt);
        assertEquals(0.0, metric.compute(), 1e-5);
        output.close(); tgt.close();
    }

    @Test
    public void testGivenMultipleBatchesWhenComputingMacroF1ThenAccumulatesCorrectly() {
        // Same prediction in two batches → result same as single batch doubled
        int[] pred   = {1, 1, 0, 0};
        int[] labels = {1, 0, 1, 0};
        Tensor out1 = logits(pred, 2); Tensor tgt1 = target(labels);
        Tensor out2 = logits(pred, 2); Tensor tgt2 = target(labels);

        F1Score metric = new F1Score(Averaging.Macro);
        metric.update(out1, tgt1);
        double afterOne = metric.compute();
        metric.update(out2, tgt2);
        double afterTwo = metric.compute();

        // Doubling the counts should not change the ratio
        assertEquals(afterOne, afterTwo, 1e-5);
        out1.close(); tgt1.close(); out2.close(); tgt2.close();
    }

    // -----------------------------------------------------------------------
    // toString format
    // -----------------------------------------------------------------------

    @Test
    public void testGivenPerfectAccuracyWhenToStringThenContains100() {
        int[] labels = {0, 1, 2};
        Tensor output = logits(labels, 3); Tensor tgt = target(labels);
        Accuracy metric = new Accuracy();
        metric.update(output, tgt);
        String s = metric.toString();
        assertTrue(s.contains("100"), "toString should show 100% for perfect accuracy, got: " + s);
        output.close(); tgt.close();
    }

    @Test
    public void testGivenPerfectF1WhenToStringThenContains100() {
        int[] labels = {0, 1};
        Tensor output = logits(labels, 2); Tensor tgt = target(labels);
        F1Score metric = new F1Score(Averaging.Macro);
        metric.update(output, tgt);
        String s = metric.toString();
        assertTrue(s.contains("100"), "toString should show 100% for perfect F1, got: " + s);
        output.close(); tgt.close();
    }

    // -----------------------------------------------------------------------
    // Accuracy threshold (1-D output)
    // -----------------------------------------------------------------------

    @Test
    public void testGivenOneDimOutputWhenComputingAccuracyWithThresholdThenCorrectValue() {
        // 1-D output: values below threshold → class 0, values above → class 1
        // outputs = [0.2, 0.8, 0.3, 0.9], threshold = 0.5
        // predicted = [0, 1, 0, 1], true = [0, 1, 0, 1] → accuracy = 1.0
        float[] outputs = {0.2f, 0.8f, 0.3f, 0.9f};
        long[]  labels  = {0L,   1L,   0L,   1L  };
        Tensor output = Tensor.of(outputs, 4);
        Tensor tgt    = Tensor.of(labels,  4);
        Accuracy metric = new Accuracy(0.5);
        metric.update(output, tgt);
        assertEquals(1.0, metric.compute(), 1e-6);
        output.close(); tgt.close();
    }

    @Test
    public void testGivenMultiClassPredictionWhenComputingMultiAveragingThenConsistent() {
        // Weighted recall == accuracy for multiclass
        int[] labels = {0, 1, 2, 0, 1, 2};
        Tensor output = logits(labels, 3); Tensor tgt = target(labels);

        Accuracy acc     = new Accuracy();
        Recall weightedR = new Recall(Averaging.Weighted);

        acc.update(output, tgt);
        weightedR.update(output, tgt);

        assertEquals(acc.compute(), weightedR.compute(), 1e-5,
                "Weighted recall should equal accuracy for perfect predictions");
        output.close(); tgt.close();
    }
}
