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
package smile.llm.model.qwen;

import org.junit.jupiter.api.*;
import smile.deep.tensor.Tensor;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HuggingFace-style partial RoPE ({@code rotate_half}).
 *
 * @author Haifeng Li
 */
public class PartialRotaryEncodingTest {

    @Test
    public void testGivenRotateHalfWhenAppliedThenSwapsNegatesHalves() {
        // [1, 2, 3, 4] → [-3, -4, 1, 2]
        Tensor x = Tensor.of(new float[]{1f, 2f, 3f, 4f}, 1, 1, 1, 4);
        Tensor y = PartialRotaryEncoding.rotateHalf(x);
        float[] out = y.contiguous().floatArray();
        assertEquals(-3f, out[0], 1e-5f);
        assertEquals(-4f, out[1], 1e-5f);
        assertEquals(1f, out[2], 1e-5f);
        assertEquals(2f, out[3], 1e-5f);
        x.close();
        y.close();
    }

    @Test
    public void testGivenCosSinTableWhenComputedThenShapeMatchesRotaryDim() {
        int rotaryDim = 8;
        int end = 16;
        var table = PartialRotaryEncoding.computeCosSin(rotaryDim, end, 10000.0);
        assertArrayEquals(new long[]{end, rotaryDim}, table.cos().shape());
        assertArrayEquals(new long[]{end, rotaryDim}, table.sin().shape());
        // First half of cos[0] equals second half (HF cat(freqs, freqs) layout).
        for (int i = 0; i < rotaryDim / 2; i++) {
            assertEquals(table.cos().getFloat(0, i), table.cos().getFloat(0, i + rotaryDim / 2), 1e-5f);
        }
        // Position 0: all angles 0 → cos=1, sin=0.
        for (int i = 0; i < rotaryDim; i++) {
            assertEquals(1f, table.cos().getFloat(0, i), 1e-5f);
            assertEquals(0f, table.sin().getFloat(0, i), 1e-5f);
        }
        table.close();
    }

    @Test
    public void testGivenIdentityPositionWhenApplyThenInputUnchangedOnRotarySlice() {
        int batch = 1, seq = 1, heads = 2, headDim = 8, rotaryDim = 4;
        Tensor xq = Tensor.ones(batch, seq, heads, headDim);
        Tensor xk = Tensor.ones(batch, seq, heads, headDim);
        var table = PartialRotaryEncoding.computeCosSin(rotaryDim, 4, 10000.0);
        // Position 0: cos=1, sin=0 → identity rotation. Slice as [1, R].
        try (var pos = smile.deep.tensor.Index.slice(0, 1);
             Tensor cos = table.cos().get(pos);
             Tensor sin = table.sin().get(pos)) {
            var out = PartialRotaryEncoding.apply(xq, xk, cos, sin, rotaryDim);
            float[] q = out._1().contiguous().floatArray();
            for (float v : q) {
                assertEquals(1f, v, 1e-4f);
            }
            out._1().close();
            out._2().close();
        }
        xq.close();
        xk.close();
        table.close();
    }

    @Test
    public void testGivenGatherWhenTwoPositionsThenShapeIsBatchSeqRot() {
        int rotaryDim = 8;
        var table = PartialRotaryEncoding.computeCosSin(rotaryDim, 64, 10000.0);
        int[] positions = {2, 11};
        Tensor cos = PartialRotaryEncoding.gather(table.cos(), positions);
        Tensor sin = PartialRotaryEncoding.gather(table.sin(), positions);
        assertArrayEquals(new long[]{2, 1, rotaryDim}, cos.shape());
        assertArrayEquals(new long[]{2, 1, rotaryDim}, sin.shape());

        Tensor xq = Tensor.ones(2, 1, 2, 8);
        Tensor cosB = PartialRotaryEncoding.broadcastCosSin(cos, xq);
        assertArrayEquals(new long[]{2, 1, 1, rotaryDim}, cosB.shape());

        cos.close();
        sin.close();
        xq.close();
        table.close();
    }
}
