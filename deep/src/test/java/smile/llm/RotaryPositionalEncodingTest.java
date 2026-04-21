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
package smile.llm;

import org.junit.jupiter.api.*;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RotaryPositionalEncoding}.
 *
 * @author Haifeng Li
 */
public class RotaryPositionalEncodingTest {

    // -----------------------------------------------------------------------
    // computeFreqCis — shape
    // -----------------------------------------------------------------------

    @Test
    public void testGivenComputeFreqCisWhenCalledThenOutputShapeIsCorrect() {
        // dim=128, end=4096 → shape [4096, 64] (complex)
        var cis = RotaryPositionalEncoding.computeFreqCis(128, 4096, 500000.0, false);
        assertArrayEquals(new long[]{4096, 64}, cis.shape());
        cis.close();
    }

    @Test
    public void testGivenScaledComputeFreqCisWhenCalledThenOutputShapeIsCorrect() {
        var cis = RotaryPositionalEncoding.computeFreqCis(128, 4096, 500000.0, true);
        assertArrayEquals(new long[]{4096, 64}, cis.shape());
        cis.close();
    }

    // -----------------------------------------------------------------------
    // computeFreqCis — position 0 must be cos(0)=1, sin(0)=0 for all heads
    // -----------------------------------------------------------------------

    @Test
    public void testGivenComputeFreqCisAtPositionZeroWhenViewedAsRealThenCosOneAndSinZero() {
        var cis = RotaryPositionalEncoding.computeFreqCis(128, 4096, 500000.0, false);
        var real = cis.viewAsReal();
        // At position 0, cos=1 and sin=0 for all frequency dimensions
        assertEquals(1.0f, real.getFloat(0, 0, 0), 1e-4f);
        assertEquals(0.0f, real.getFloat(0, 0, 1), 1e-4f);
        assertEquals(1.0f, real.getFloat(0, 1, 0), 1e-4f);
        assertEquals(0.0f, real.getFloat(0, 1, 1), 1e-4f);
        cis.close();
    }

    // -----------------------------------------------------------------------
    // computeFreqCis — position 1 spot-check
    // -----------------------------------------------------------------------

    @Test
    public void testGivenComputeFreqCisAtPositionOneThenFirstHeadMatchesKnownValues() {
        var cis = RotaryPositionalEncoding.computeFreqCis(128, 4096, 500000.0, false);
        var real = cis.viewAsReal();
        assertEquals(0.5403f, real.getFloat(1, 0, 0), 1e-4f);
        assertEquals(0.8415f, real.getFloat(1, 0, 1), 1e-4f);
        assertEquals(0.6861f, real.getFloat(1, 1, 0), 1e-4f);
        assertEquals(0.7275f, real.getFloat(1, 1, 1), 1e-4f);
        cis.close();
    }

    // -----------------------------------------------------------------------
    // computeFreqCis — last position
    // -----------------------------------------------------------------------

    @Test
    public void testGivenComputeFreqCisAtLastPositionThenValuesMatchKnownGolden() {
        var cis = RotaryPositionalEncoding.computeFreqCis(128, 4096, 500000.0, false);
        var real = cis.viewAsReal();
        assertEquals(0.9999f, real.getFloat(4095, 63, 0), 1e-4f);
        assertEquals(0.0101f, real.getFloat(4095, 63, 1), 1e-4f);
        cis.close();
    }

    // -----------------------------------------------------------------------
    // computeFreqCis — scaled RoPE (last position differs from unscaled)
    // -----------------------------------------------------------------------

    @Test
    public void testGivenScaledComputeFreqCisAtLastPositionThenValuesDifferFromUnscaled() {
        var cis = RotaryPositionalEncoding.computeFreqCis(128, 4096, 500000.0, true);
        var real = cis.viewAsReal();
        // scaled produces different sin at high frequency / long range
        assertEquals(0.9999f, real.getFloat(4095, 63, 0), 1e-4f);
        assertEquals(0.00126f, real.getFloat(4095, 63, 1), 1e-5f);
        cis.close();
    }

    // -----------------------------------------------------------------------
    // reshapeForBroadcast
    // -----------------------------------------------------------------------

    @Test
    public void testGivenReshapeForBroadcastWhenCalledThenNonSeqDimsCollapsedToOne() {
        // cis: [seqlen, headDim/2] complex → shape [8, 4]
        // x: [batch, seqlen, numHeads, headDim/2] complex → shape [2, 8, 4, 4]
        Tensor cis = Tensor.rand(8, 4); // stand-in for complex shape
        Tensor x   = Tensor.rand(2, 8, 4, 4);

        Tensor reshaped = RotaryPositionalEncoding.reshapeForBroadcast(cis, x);
        // dim=4 → shape should be [1, seqlen=8, 1, headDim/2=4]
        assertArrayEquals(new long[]{1, 8, 1, 4}, reshaped.shape());
        cis.close(); x.close();
    }

    // -----------------------------------------------------------------------
    // apply — output types match input types
    // -----------------------------------------------------------------------

    @Test
    public void testGivenApplyWhenCalledThenOutputDtypeMatchesInput() {
        // Tiny test: batch=1, seqlen=2, numHeads=2, headDim=4 (must be even)
        int batchSize = 1, seqLen = 2, numHeads = 2, headDim = 4;
        Tensor xq  = Tensor.rand(batchSize, seqLen, numHeads, headDim).to(ScalarType.Float32);
        Tensor xk  = Tensor.rand(batchSize, seqLen, numHeads, headDim).to(ScalarType.Float32);
        Tensor cis = RotaryPositionalEncoding.computeFreqCis(headDim, seqLen, 10000.0, false);

        var out = RotaryPositionalEncoding.apply(xq, xk, cis);
        assertEquals(xq.dtype(), out._1().dtype(), "xq output dtype must match input");
        assertEquals(xk.dtype(), out._2().dtype(), "xk output dtype must match input");
        // shapes preserved
        assertArrayEquals(xq.shape(), out._1().shape());
        assertArrayEquals(xk.shape(), out._2().shape());

        xq.close(); xk.close(); cis.close();
        out._1().close(); out._2().close();
    }
}
