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
package smile.llm.quant;

import org.junit.jupiter.api.*;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MxFp8}.
 *
 * @author Haifeng Li
 */
public class MxFp8Test {
    private static final float[] PALETTE = {1.0f, -2.0f, 0.5f, 3.0f, -0.25f, 1.5f, 6.0f, -0.125f};

    @Test
    public void testGivenE8m0CodesWhenConvertedThenArePowersOfTwo() {
        assertEquals(1.0f, MxFp8.e8m0ToFloat(127));
        assertEquals(8.0f, MxFp8.e8m0ToFloat(130));
        assertEquals(0.5f, MxFp8.e8m0ToFloat(126));
        assertEquals((float) Math.scalb(1.0, -127), MxFp8.e8m0ToFloat(0));
        assertTrue(Float.isNaN(MxFp8.e8m0ToFloat(255)));
    }

    @Test
    public void testGivenBlockScalesWhenDequantizedThenEachBlockUsesItsOwnScale() {
        int n = 2;
        int k = 64;
        float[] values = new float[n * k];
        for (int i = 0; i < values.length; i++) {
            values[i] = PALETTE[i % PALETTE.length];
        }
        int[] codes = {127, 130, 120, 134};  // 1, 8, 2^-7, 2^7
        try (var noGrad = Tensor.noGradGuard();
             Tensor fp = Tensor.of(values, n, k).to(ScalarType.Float8e4m3fn);
             Tensor sc = Tensor.of(codes, n, k / MxFp8.BLOCK).to(ScalarType.UInt8);
             Tensor out = MxFp8.dequantize(fp, sc, ScalarType.Float)) {
            assertArrayEquals(new long[]{n, k}, out.shape());
            float[] got = out.copy().floatArray();
            for (int i = 0; i < got.length; i++) {
                int block = (i / k) * (k / MxFp8.BLOCK) + (i % k) / MxFp8.BLOCK;
                assertEquals(values[i] * MxFp8.e8m0ToFloat(codes[block]), got[i], 0.0f, "element " + i);
            }
        }
    }

    @Test
    public void testGivenNanScaleWhenDequantizedThenBlockIsNan() {
        try (var noGrad = Tensor.noGradGuard();
             Tensor fp = Tensor.of(new float[64], 1, 64).to(ScalarType.Float8e4m3fn);
             Tensor sc = Tensor.of(new int[]{127, 255}, 1, 2).to(ScalarType.UInt8);
             Tensor out = MxFp8.dequantize(fp, sc, ScalarType.Float)) {
            float[] got = out.copy().floatArray();
            assertEquals(0.0f, got[0]);
            assertTrue(Float.isNaN(got[32]));
        }
    }

    @Test
    public void testGivenWrongScaleShapeWhenDequantizedThenRejected() {
        try (var noGrad = Tensor.noGradGuard();
             Tensor fp = Tensor.of(new float[64], 1, 64).to(ScalarType.Float8e4m3fn);
             Tensor sc = Tensor.of(new int[]{127}, 1, 1).to(ScalarType.UInt8)) {
            assertThrows(IllegalArgumentException.class, () -> MxFp8.dequantize(fp, sc, ScalarType.Float));
        }
    }
}
