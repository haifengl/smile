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
package smile.llm.cache;

import org.junit.jupiter.api.*;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link KvCachePool}.
 *
 * @author Haifeng Li
 */
public class KvCachePoolTest {

    private static KvCacheLayout tinyLayout(int layers, int batch, int seq) {
        // dim=64, heads=4 → headDim=16, kvHeads=2
        return KvCacheLayout.of(layers, 64, 4, 2, batch, seq);
    }

    @Test
    public void testGivenForTestingPoolWhenBoundThenPutGetRoundTrip() {
        // Given
        KvCacheLayout layout = tinyLayout(2, 1, 32);
        try (var pool = KvCachePool.forTesting(layout, Device.CPU())) {
            assertEquals(1 * 32, pool.numSlots());
            pool.bindRequests(1, 16);

            // When – write 4 tokens at startPos=0
            Tensor k = Tensor.ones(1, 4, 2, 16);
            Tensor v = Tensor.full(2.0f, 1, 4, 2, 16);
            pool.put(0, 0, k, v);

            // Then
            var cached = pool.get(0, 4);
            assertArrayEquals(new long[]{1, 4, 2, 16}, cached._1().shape());
            assertEquals(1.0f, cached._1().getFloat(0, 0, 0, 0), 1e-5);
            assertEquals(2.0f, cached._2().getFloat(0, 0, 0, 0), 1e-5);
            cached._1().close();
            cached._2().close();
            k.close();
            v.close();
        }
    }

    @Test
    public void testGivenPoolWhenAllocateOnCpuThenSizedToMaxBatchTimesSeq() {
        // Given / When – CPU path ignores memFraction and sizes to batch×seq
        KvCacheLayout layout = tinyLayout(2, 2, 64);
        try (var pool = KvCachePool.allocate(layout, Device.CPU(), ScalarType.Float, 0.85)) {
            // Then – at least maxBatchSize × maxSeqLen, page-aligned
            assertTrue(pool.numSlots() >= 2 * 64);
            assertEquals(0, pool.numSlots() % pool.pageSize());
        }
    }

    @Test
    public void testGivenBoundRequestsWhenUnboundThenPagesReturnToFreeList() {
        // Given
        try (var pool = new KvCachePool(1, 64, 2, 16, 16, Device.CPU(), ScalarType.Float)) {
            int freeBefore = pool.freePages();
            pool.bindRequests(1, 32);
            assertTrue(pool.freePages() < freeBefore);

            // When
            pool.unbindRequests();

            // Then
            assertEquals(freeBefore, pool.freePages());
        }
    }

    @Test
    public void testGivenInsufficientPagesWhenBindThenThrows() {
        // Given – tiny pool of 16 slots (1 page)
        try (var pool = new KvCachePool(1, 16, 2, 16, 16, Device.CPU(), ScalarType.Float)) {
            // When / Then – request more than available
            assertThrows(IllegalStateException.class, () -> pool.bindRequests(2, 16));
        }
    }
}
