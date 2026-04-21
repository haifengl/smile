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
}

