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
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit tests for Marlin weight packing (CPU path).
 *
 * @author Haifeng Li
 */
public class MarlinWeightPackerTest {

    @BeforeEach
    public void requireNativeLib() {
        try {
            Tensor.of(new float[]{1f}).close();
        } catch (Throwable t) {
            assumeTrue(false, "libsmile_torch unavailable: " + t.getMessage());
        }
    }

    @Test
    public void testGivenFp16WeightWhenPackThenMarlinShapesMatch() {
        // Given – Marlin requires out % 256 == 0 and in % 128 == 0
        int outFeatures = 256;
        int inFeatures = 128;
        float[] data = new float[outFeatures * inFeatures];
        for (int i = 0; i < data.length; i++) {
            data[i] = (i % 17) * 0.01f - 0.08f;
        }
        Tensor w = Tensor.of(data).reshape(outFeatures, inFeatures).to(ScalarType.Half);

        // When
        var packed = MarlinWeightPacker.packFromFp16(w, 128, Device.CPU());
        w.close();

        // Then – upstream B is [k/16, n*16/8]
        assertEquals(outFeatures, packed.outFeatures());
        assertEquals(inFeatures, packed.inFeatures());
        assertEquals(128, packed.groupSize());
        assertArrayEquals(new long[]{inFeatures / 16L, outFeatures * 16L / 8L}, packed.qweight().shape());
        assertArrayEquals(new long[]{1L, outFeatures}, packed.scales().shape());
        packed.close();
    }

    /**
     * AutoAWQ pack order: nibble slot {@code i} stores logical column
     * {@code AWQ_ORDER[i]}. Inverse is {@code AWQ_REVERSE_ORDER}.
     */
    private static final int[] AWQ_ORDER = {0, 2, 4, 6, 1, 3, 5, 7};

    @Test
    public void testGivenAwqLayoutWhenPackThenInFeaturesMatchHidden() {
        // Given – AWQ shapes for Linear(in=128, out=256), groupSize=128
        int inFeatures = 128;
        int outFeatures = 256;
        int groupSize = 128;
        int packedOut = outFeatures / 8;
        int numGroups = inFeatures / groupSize;

        int[] qweight = new int[inFeatures * packedOut];
        int[] qzeros = new int[numGroups * packedOut];
        float[] scales = new float[numGroups * outFeatures];
        for (int o = 0; o < outFeatures; o++) {
            scales[o] = 0.1f;
        }
        for (int k = 0; k < inFeatures; k++) {
            for (int po = 0; po < packedOut; po++) {
                int word = 0;
                for (int slot = 0; slot < 8; slot++) {
                    int logical = AWQ_ORDER[slot];
                    int qi = (po * 8 + logical) % 8;
                    word |= (qi & 0xF) << (4 * slot);
                }
                qweight[k * packedOut + po] = word;
            }
        }

        Tensor qw = Tensor.of(qweight).reshape(inFeatures, packedOut);
        Tensor sc = Tensor.of(scales).reshape(numGroups, outFeatures);
        Tensor qz = Tensor.of(qzeros).reshape(numGroups, packedOut);

        var packed = MarlinWeightPacker.packAwq(qw, sc, qz, groupSize, Device.CPU());
        qw.close();
        sc.close();
        qz.close();

        assertEquals(inFeatures, packed.inFeatures(),
                "AWQ dequant must keep inFeatures=K, not K*8 from GPTQ unpack");
        assertEquals(outFeatures, packed.outFeatures());
        assertArrayEquals(new long[]{inFeatures / 16L, outFeatures * 16L / 8L}, packed.qweight().shape());
        packed.close();
    }
}
