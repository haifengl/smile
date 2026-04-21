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
package smile.deep;

import org.junit.jupiter.api.*;
import smile.deep.tensor.Tensor;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Loss} factory methods.
 * Each test verifies that the functor produces a finite scalar (or expected-shape) tensor
 * given simple known inputs.
 *
 * @author Haifeng Li
 */
public class LossTest {

    // Helpers ----------------------------------------------------------------

    /** A simple 1-D float tensor. */
    private static Tensor floatTensor(float... values) {
        return Tensor.of(values, values.length);
    }

    // -----------------------------------------------------------------------

    @Test
    public void testGivenL1LossWhenAppliedThenReturnsFiniteScalar() {
        Tensor input  = floatTensor(1.0f, 2.0f, 3.0f);
        Tensor target = floatTensor(1.5f, 2.5f, 3.5f);
        Tensor loss   = Loss.l1().apply(input, target);
        assertEquals(0, loss.dim(), "l1 loss should be a scalar");
        assertTrue(Double.isFinite(loss.doubleValue()));
        assertEquals(0.5, loss.doubleValue(), 1e-5);
        input.close(); target.close(); loss.close();
    }

    @Test
    public void testGivenMseLossWhenAppliedThenReturnsFiniteScalar() {
        Tensor input  = floatTensor(0.0f);
        Tensor target = floatTensor(2.0f);
        Tensor loss   = Loss.mse().apply(input, target);
        assertEquals(0, loss.dim(), "mse loss should be a scalar");
        assertEquals(4.0, loss.doubleValue(), 1e-5); // (0-2)^2 = 4
        input.close(); target.close(); loss.close();
    }

    @Test
    public void testGivenSmoothL1LossWhenAppliedThenReturnsFiniteScalar() {
        Tensor input  = floatTensor(1.0f, 2.0f);
        Tensor target = floatTensor(1.5f, 2.5f);
        Tensor loss   = Loss.smoothL1().apply(input, target);
        assertTrue(Double.isFinite(loss.doubleValue()));
        input.close(); target.close(); loss.close();
    }

    @Test
    public void testGivenHuberLossWithDeltaWhenAppliedThenDifferentFromDefault() {
        // Use error = 2.0 so the two deltas produce different formulas:
        //   delta=1.0 → L1 branch: 1.0*(2.0 - 0.5*1.0) = 1.5
        //   delta=10.0 → quadratic branch: 0.5*(2.0²)/10.0 = 0.2  (PyTorch normalises by delta)
        // The key check is that delta is actually passed through (not ignored).
        Tensor input   = floatTensor(2.0f);
        Tensor target  = floatTensor(0.0f);
        Tensor huber1  = Loss.huber(1.0).apply(input, target);
        Tensor huber10 = Loss.huber(10.0).apply(input, target);
        assertTrue(Double.isFinite(huber1.doubleValue()));
        assertTrue(Double.isFinite(huber10.doubleValue()));
        assertNotEquals(huber1.doubleValue(), huber10.doubleValue(), 1e-9,
                "huber(delta) should pass delta through; different deltas should yield different losses");
        input.close(); target.close(); huber1.close(); huber10.close();
    }

    @Test
    public void testGivenBceLossWhenAppliedThenReturnsFiniteScalar() {
        Tensor input  = floatTensor(0.9f, 0.1f);
        Tensor target = floatTensor(1.0f, 0.0f);
        Tensor loss   = Loss.bce().apply(input, target);
        assertTrue(Double.isFinite(loss.doubleValue()), "bce loss must be finite");
        input.close(); target.close(); loss.close();
    }

    @Test
    public void testGivenBceWithLogitsLossWhenAppliedThenReturnsFiniteScalar() {
        Tensor input  = floatTensor(2.0f, -2.0f);
        Tensor target = floatTensor(1.0f,  0.0f);
        Tensor loss   = Loss.bceWithLogits().apply(input, target);
        assertTrue(Double.isFinite(loss.doubleValue()), "bceWithLogits loss must be finite");
        input.close(); target.close(); loss.close();
    }

    @Test
    public void testGivenKlDivLossWhenAppliedThenReturnsFiniteValue() {
        // kl_div expects log-probabilities as input
        Tensor input  = Tensor.of(new float[]{-0.5f, -0.9f}, 2);
        Tensor target = Tensor.of(new float[]{ 0.6f,  0.4f}, 2);
        Tensor loss   = Loss.kl().apply(input, target);
        assertTrue(Double.isFinite(loss.doubleValue()), "kl loss must be finite");
        input.close(); target.close(); loss.close();
    }

    // -----------------------------------------------------------------------
    // Loss — crossEntropy and marginRanking
    // -----------------------------------------------------------------------

    @Test
    public void testGivenCrossEntropyLossWhenAppliedThenReturnsFinitePositiveScalar() {
        Tensor input  = Tensor.of(new float[]{2f, 1f, 0.1f, 0.1f, 2f, 0.1f}, 2, 3);
        Tensor target = Tensor.of(new long[]{0L, 1L}, 2L);
        Tensor loss   = Loss.crossEntropy().apply(input, target);
        assertEquals(0, loss.dim(), "cross-entropy loss must be a scalar");
        assertTrue(loss.doubleValue() > 0, "cross-entropy loss must be positive");
        input.close(); target.close(); loss.close();
    }

    @Test
    public void testGivenNllLossWhenAppliedThenReturnsFiniteScalar() {
        // nll_loss expects log-probabilities; use raw log-prob values directly
        Tensor input  = Tensor.of(new float[]{-0.5f, -1.3f, -0.9f, -1.2f, -0.4f, -1.1f}, 2, 3);
        Tensor target = Tensor.of(new long[]{0L, 2L}, 2L);
        Tensor loss   = Loss.nll().apply(input, target);
        assertTrue(Double.isFinite(loss.doubleValue()), "nll loss must be finite");
        input.close(); target.close(); loss.close();
    }

    @Test
    public void testGivenHingeEmbeddingLossWhenAppliedThenReturnsFiniteValue() {
        Tensor input  = Tensor.of(new float[]{0.5f, -0.5f}, 2);
        Tensor target = Tensor.of(new float[]{1f, -1f}, 2);
        Tensor loss   = Loss.hingeEmbedding().apply(input, target);
        assertTrue(Double.isFinite(loss.doubleValue()));
        input.close(); target.close(); loss.close();
    }

    @Test
    public void testGivenMarginRankingLossWhenAppliedThenReturnsFiniteValue() {
        Tensor a      = Tensor.of(new float[]{1f}, 1);
        Tensor b      = Tensor.of(new float[]{-1f}, 1);
        Tensor target = Tensor.of(new float[]{1f}, 1);  // a should rank higher
        Tensor loss   = Loss.marginRanking(a, b, target);
        assertTrue(Double.isFinite(loss.doubleValue()));
        a.close(); b.close(); target.close(); loss.close();
    }

    @Test
    public void testGivenTripleMarginRankingLossWhenAppliedThenReturnsFiniteValue() {
        Tensor anchor   = Tensor.of(new float[]{0f, 1f}, 2);
        Tensor positive = Tensor.of(new float[]{0f, 1f}, 2);
        Tensor negative = Tensor.of(new float[]{1f, 0f}, 2);
        Tensor loss = Loss.tripleMarginRanking(anchor, positive, negative);
        assertTrue(Double.isFinite(loss.doubleValue()));
        anchor.close(); positive.close(); negative.close(); loss.close();
    }
}

