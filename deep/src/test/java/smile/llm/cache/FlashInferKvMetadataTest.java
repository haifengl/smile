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

import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import static org.junit.jupiter.api.Assertions.*;

/**
 * CSR metadata for FlashInfer / paged attention.
 */
public class FlashInferKvMetadataTest {
    @Test
    public void testGivenBoundRequestWhenBuildMetadataThenCsrMatchesLength() {
        int pageSize = 16;
        int slots = pageSize * 4;
        try (var pool = new KvCachePool(1, slots, 2, 8, pageSize, Device.CPU(), ScalarType.Float)) {
            pool.bindRequests(1, 20);
            try (FlashInferKvMetadata meta = pool.buildFlashInferMetadata(20)) {
                assertEquals(pageSize, meta.pageSize());
                assertArrayEquals(new long[]{2}, meta.pagedKvIndptr().shape()); // B+1 = 2
                assertEquals(0, meta.pagedKvIndptr().intArray()[0]);
                assertEquals(2, meta.pagedKvIndptr().intArray()[1]); // ceil(20/16)=2 pages
                assertEquals(2, meta.pagedKvIndices().length());
                assertEquals(4, meta.pagedKvLastPageLen().intArray()[0]); // 20 % 16
                // Contiguous bind: physical pages are consecutive from page 0.
                assertEquals(0, meta.pagedKvIndices().intArray()[0]);
                assertEquals(1, meta.pagedKvIndices().intArray()[1]);
            }
        }
    }
}
