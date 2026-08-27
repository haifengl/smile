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
import smile.deep.tensor.Tensor;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests for {@link QuantTpSharding}.
 *
 * @author Haifeng Li
 */
public class QuantTpShardingTest {

    @BeforeEach
    public void requireNativeLib() {
        try {
            Tensor.of(new float[]{1f}).close();
        } catch (Throwable t) {
            assumeTrue(false, "libsmile_torch unavailable: " + t.getMessage());
        }
    }

    @Test
    public void testGivenColumnShardWhenTp2ThenHalfRows() {
        float[] data = new float[8 * 4];
        for (int i = 0; i < data.length; i++) {
            data[i] = i;
        }
        Tensor w = Tensor.of(data).reshape(8, 4);
        try (Tensor r0 = QuantTpSharding.shardColumn(w, 2, 0);
             Tensor r1 = QuantTpSharding.shardColumn(w, 2, 1)) {
            assertArrayEquals(new long[]{4, 4}, r0.shape());
            assertArrayEquals(new long[]{4, 4}, r1.shape());
            float[] a0 = r0.to(Device.CPU()).floatArray();
            assertEquals(0f, a0[0], 1e-6f);
            float[] a1 = r1.to(Device.CPU()).floatArray();
            assertEquals(16f, a1[0], 1e-6f); // row 4 starts at index 16
        } finally {
            w.close();
        }
    }
}
