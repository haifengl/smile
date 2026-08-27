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
    public void testGivenFp16WeightWhenPackThenShapesMatch() {
        // Given – small [out=32, in=128] weight, groupSize=128
        float[] data = new float[32 * 128];
        for (int i = 0; i < data.length; i++) {
            data[i] = (i % 17) * 0.01f - 0.08f;
        }
        Tensor w = Tensor.of(data).reshape(32, 128).to(ScalarType.Half);

        // When
        var packed = MarlinWeightPacker.packFromFp16(w, 128, Device.CPU());
        w.close();

        // Then
        assertEquals(32, packed.outFeatures());
        assertEquals(128, packed.inFeatures());
        assertEquals(128, packed.groupSize());
        assertEquals(2, packed.qweight().shape().length);
        assertEquals(2, packed.scales().shape().length);
        assertEquals(1, packed.scales().shape()[0]); // one group
        assertEquals(32, packed.scales().shape()[1]);
        packed.close();
    }
}
