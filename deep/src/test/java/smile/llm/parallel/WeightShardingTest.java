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
package smile.llm.parallel;

import smile.deep.tensor.Tensor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * CPU tests for column/row weight sharding helpers.
 *
 * @author Haifeng Li
 */
public class WeightShardingTest {

    @Test
    public void testGivenColumnParallelWhenSplitThenBandsMatch() {
        float[] data = {
                0, 1, 2, 3,
                4, 5, 6, 7,
                8, 9, 10, 11,
                12, 13, 14, 15
        };
        Tensor w = Tensor.of(data, 4, 4);
        Tensor r0 = WeightSharding.columnParallel(w, 2, 0).contiguous();
        Tensor r1 = WeightSharding.columnParallel(w, 2, 1).contiguous();
        assertArrayEquals(new long[]{2, 4}, r0.shape());
        assertArrayEquals(new long[]{2, 4}, r1.shape());
        assertEquals(0f, r0.getFloat(0, 0), 1e-5);
        assertEquals(8f, r1.getFloat(0, 0), 1e-5);
        w.close();
        r0.close();
        r1.close();
    }

    @Test
    public void testGivenRowParallelWhenSplitThenInBandsMatch() {
        float[] data = {
                0, 1, 2, 3,
                4, 5, 6, 7
        };
        Tensor w = Tensor.of(data, 2, 4);
        Tensor r0 = WeightSharding.rowParallel(w, 2, 0).contiguous();
        Tensor r1 = WeightSharding.rowParallel(w, 2, 1).contiguous();
        assertArrayEquals(new long[]{2, 2}, r0.shape());
        assertEquals(0f, r0.getFloat(0, 0), 1e-5);
        assertEquals(2f, r1.getFloat(0, 0), 1e-5);
        w.close();
        r0.close();
        r1.close();
    }

    @Test
    public void testGivenTpOneWhenColumnParallelThenIdentity() {
        Tensor w = Tensor.of(new float[]{1, 2, 3, 4}, 2, 2);
        Tensor out = WeightSharding.columnParallel(w, 1, 0);
        assertSame(w, out);
        w.close();
    }
}
