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
        // Given / When – CPU path sizes to batch×seq and must not overshoot
        KvCacheLayout layout = tinyLayout(2, 2, 64);
        try (var pool = KvCachePool.allocate(layout, Device.CPU(), ScalarType.Float, 0.85)) {
            // Then – exactly maxBatchSize × maxSeqLen, page-aligned
            assertEquals(2 * 64, pool.numSlots());
            assertEquals(0, pool.numSlots() % pool.pageSize());
        }
    }

    @Test
    public void testGivenAllocateWhenMaxSeqThenNotRaisedAboveConfigured() {
        // Given
        KvCacheLayout layout = tinyLayout(1, 1, 128);
        // When
        try (var pool = KvCachePool.allocate(layout, Device.CPU(), ScalarType.Float, 1.0)) {
            // Then – capped at maxBatchSize*maxSeqLen (never inflated past config)
            assertEquals(128, pool.numSlots());
        }
    }

    @Test
    public void testGivenStaticBudgetWhenUsedSubtractedThenSlotsMatchFormula() {
        // Given – 40 GiB total, 10 GiB used (weights), y=0.85 → static=34 GiB, kv=24 GiB
        long total = 40L << 30;
        long used = 10L << 30;
        long free = total - used;
        double y = 0.85;
        long bytesPerToken = 1024L;
        int pageSize = 16;
        int maxBatch = 1;
        int maxSeq = 8192;

        // When
        var budget = KvCachePool.computeStaticKvBudget(
                total, free, y, bytesPerToken, pageSize, maxBatch, maxSeq);

        // Then
        long expectedStatic = (long) (total * y);
        long softMargin = Math.min(1L << 30, Math.max(512L << 20, total / 40));
        assertEquals(total, budget.total());
        assertEquals(used, budget.used());
        assertEquals(expectedStatic, budget.staticBudget());
        assertEquals(expectedStatic - used - softMargin, budget.kvBudget());
        assertEquals(total - expectedStatic, budget.dynamicReserve());
        assertEquals(maxBatch * maxSeq, budget.maxUsefulSlots());
        // 24 GiB / 1024 ≫ 8192 → capped at context
        assertEquals(8192, budget.numSlots());
    }

    @Test
    public void testGivenTightKvBudgetWhenComputeThenSlotsBelowContextCap() {
        // Given – static region barely larger than used → small KV budget
        long total = 20L << 30;
        long used = (long) (total * 0.85) - (512L << 20); // leave ~512 MiB for KV
        long free = total - used;
        double y = 0.85;
        long bytesPerToken = 2L * 32 * 8 * 128 * 2; // layers×kvHeads×headDim×fp16 ×2 (K+V)
        int pageSize = 16;
        int maxSeq = 8192;

        // When
        var budget = KvCachePool.computeStaticKvBudget(
                total, free, y, bytesPerToken, pageSize, 1, maxSeq);

        // Then
        assertTrue(budget.kvBudget() > 0);
        assertTrue(budget.numSlots() < budget.maxUsefulSlots());
        assertEquals(0, budget.numSlots() % pageSize);
        // Soft margin does not fit in the ~512 MiB KV window → fall back to raw SGLang budget.
        assertEquals((long) (total * y) - used, budget.kvBudget());
    }

    @Test
    public void testGivenKvBudgetTooSmallWhenComputeThenThrows() {
        // Given – used already exceeds staticBudget
        long total = 10L << 30;
        long free = 1L << 30; // used = 9 GiB
        double y = 0.5; // static = 5 GiB < used

        // When / Then
        assertThrows(IllegalStateException.class, () ->
                KvCachePool.computeStaticKvBudget(total, free, y, 1024L, 16, 1, 128));
    }

    @Test
    public void testGivenSlotsFromBudgetWhenPageAlignThenRespectsCap() {
        // Given / When
        var slots = KvCachePool.slotsFromBudget(100_000L, 100L, 16, 1, 64);
        // Then – 1000 tokens from budget, capped and page-aligned to 64
        assertEquals(64, slots.numSlots());
        assertEquals(64, slots.maxUsefulSlots());
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
    public void testGivenInsufficientPagesWhenBindBatchThenThrows() {
        // Given – tiny pool of 16 slots (1 page); batch of 2 cannot each get a page
        try (var pool = new KvCachePool(1, 16, 2, 16, 16, Device.CPU(), ScalarType.Float)) {
            // When / Then
            assertThrows(IllegalStateException.class, () -> pool.bindRequests(2, 16));
        }
    }

    @Test
    public void testGivenOversizedCapacityWhenBindThenClampsWithoutThrow() {
        // Given – 16-slot pool, request far more than available
        try (var pool = new KvCachePool(1, 16, 2, 16, 16, Device.CPU(), ScalarType.Float)) {
            // When
            pool.bindRequests(1, 64);

            // Then – bound to free pool size, no OOM throw
            assertEquals(16, pool.requestCapacity());
            assertEquals(0, pool.freePages());
        }
    }

    @Test
    public void testGivenBoundCapacityWhenPutBeyondThenDoesNotGrow() {
        // Given
        try (var pool = new KvCachePool(1, 32, 2, 16, 16, Device.CPU(), ScalarType.Float)) {
            pool.bindRequests(1, 16);
            assertEquals(16, pool.requestCapacity());
            int freeAfterBind = pool.freePages();

            Tensor k = Tensor.ones(1, 1, 2, 16);
            Tensor v = Tensor.ones(1, 1, 2, 16);
            try {
                // When – write past bound capacity
                assertThrows(KvCacheExhaustedException.class, () -> pool.put(0, 16, k, v));
                // Then – free pages unchanged (no growth)
                assertEquals(freeAfterBind, pool.freePages());
                assertEquals(16, pool.requestCapacity());
            } finally {
                k.close();
                v.close();
            }
        }
    }

    @Test
    public void testGivenBindWithPrefixWhenCapacityClampedThenPromptStillFits() {
        // Given – pool of 32 slots; prompt length 8; request huge generation headroom
        try (var pool = new KvCachePool(1, 32, 2, 16, 16, Device.CPU(), ScalarType.Float)) {
            int[] prompt = {1, 2, 3, 4, 5, 6, 7, 8};

            // When
            assertEquals(0, pool.bindWithPrefix(prompt, 256));

            // Then
            assertEquals(32, pool.requestCapacity());
            assertTrue(pool.requestCapacity() >= prompt.length);
            pool.unbindRequests();
        }
    }

    @Test
    public void testGivenBindWithPrefixWhenPromptExceedsPoolThenThrows() {
        // Given – 16-slot pool cannot hold a 20-token prompt
        try (var pool = new KvCachePool(1, 16, 2, 16, 16, Device.CPU(), ScalarType.Float)) {
            int[] prompt = new int[20];
            for (int i = 0; i < prompt.length; i++) {
                prompt[i] = i + 1;
            }

            // When / Then
            assertThrows(IllegalArgumentException.class, () -> pool.bindWithPrefix(prompt, 64));
        }
    }

    @Test
    public void testGivenBindWithPrefixWhenFirstRequestThenMissThenHitOnRepeat() {
        // Given – pageSize=1 so every token is eligible for the radix tree
        KvCacheLayout layout = tinyLayout(1, 1, 64);
        try (var pool = KvCachePool.forTesting(layout, Device.CPU())) {
            int[] prompt = {10, 20, 30, 40, 50, 60, 70, 80};

            // When – first request: miss, write KV, insert
            assertEquals(0, pool.bindWithPrefix(prompt, 32));
            Tensor k = Tensor.ones(1, prompt.length, 2, 16);
            Tensor v = Tensor.full(3.0f, 1, prompt.length, 2, 16);
            pool.put(0, 0, k, v);
            k.close();
            v.close();
            pool.finishRequest(prompt);

            // Then – second request with same prompt hits
            int hit = pool.bindWithPrefix(prompt, 32);
            assertEquals(prompt.length, hit);
            assertEquals(prompt.length, pool.prefixMatchTokens());
            assertTrue(pool.prefixPromptTokens() >= prompt.length);

            // Cached values still readable at prefix positions
            var cached = pool.get(0, prompt.length);
            assertEquals(3.0f, cached._2().getFloat(0, 0, 0, 0), 1e-5);
            cached._1().close();
            cached._2().close();
            pool.unbindRequests();
        }
    }

    @Test
    public void testGivenBindWithPrefixWhenDisabledThenAlwaysMiss() {
        // Given
        KvCacheLayout layout = tinyLayout(1, 1, 32);
        try (var pool = KvCachePool.forTesting(layout, Device.CPU())) {
            pool.setPrefixReuseEnabled(false);
            int[] prompt = {1, 2, 3, 4};

            // When
            assertEquals(0, pool.bindWithPrefix(prompt, 16));
            Tensor k = Tensor.ones(1, 4, 2, 16);
            Tensor v = Tensor.ones(1, 4, 2, 16);
            pool.put(0, 0, k, v);
            k.close();
            v.close();
            pool.finishRequest(prompt); // no-op path for insert when reuse was contiguous-only

            // Then – still miss because reuse was disabled at bind time
            assertEquals(0, pool.bindWithPrefix(prompt, 16));
            pool.unbindRequests();
        }
    }

    @Test
    public void testGivenFinishRequestWhenPrefixRetainedThenFreePagesStayBelowFull() {
        // Given
        try (var pool = new KvCachePool(1, 64, 2, 16, 1, Device.CPU(), ScalarType.Float)) {
            int freeFull = pool.freePages();
            int[] prompt = {1, 2, 3, 4, 5, 6, 7, 8};
            pool.bindWithPrefix(prompt, 16);
            Tensor k = Tensor.ones(1, 8, 2, 16);
            Tensor v = Tensor.ones(1, 8, 2, 16);
            pool.put(0, 0, k, v);
            k.close();
            v.close();

            // When
            pool.finishRequest(prompt);

            // Then – inserted pages remain allocated (not returned to free list)
            assertTrue(pool.freePages() < freeFull);
            assertTrue(pool.prefixInsertTokens() >= prompt.length);
        }
    }

    @Test
    public void testGivenTwoBoundRequestsWhenActivateAndUnbindOneThenOtherRemains() {
        // Given – pageSize=1 pool large enough for two concurrent requests
        try (var pool = new KvCachePool(1, 64, 2, 16, 1, Device.CPU(), ScalarType.Float)) {
            pool.setPrefixReuseEnabled(false);
            int id1 = pool.bindRequest(new int[]{1, 2, 3, 4}, 16);
            int id2 = pool.bindRequest(new int[]{5, 6, 7, 8}, 16);
            assertTrue(id1 > 0);
            assertTrue(id2 > 0);
            assertEquals(2, pool.boundRequestCount());

            // When – activate both and write a batch-2 step
            pool.activateStep(id1, id2);
            Tensor k = Tensor.ones(2, 2, 2, 16);
            Tensor v = Tensor.full(4.0f, 2, 2, 2, 16);
            try {
                pool.put(0, 0, k, v);
            } finally {
                k.close();
                v.close();
            }

            // Instant Eviction of one request
            pool.unbindRequest(id1);

            // Then – other request still bound; activation cleared for the step
            assertEquals(1, pool.boundRequestCount());
            pool.activateStep(id2);
            var cached = pool.get(0, 2);
            assertEquals(4.0f, cached._2().getFloat(0, 0, 0, 0), 1e-5);
            cached._1().close();
            cached._2().close();
            pool.unbindRequest(id2);
            assertEquals(0, pool.boundRequestCount());
        }
    }

    @Test
    public void testGivenBindRequestWhenUnbindRequestThenFreeSlotsIncrease() {
        // Given
        try (var pool = new KvCachePool(1, 64, 2, 16, 16, Device.CPU(), ScalarType.Float)) {
            pool.setPrefixReuseEnabled(false);
            int freeBefore = pool.freeSlots();
            int id = pool.bindRequest(new int[]{1, 2, 3, 4}, 32);
            assertTrue(id > 0);
            assertTrue(pool.freeSlots() < freeBefore);
            assertEquals(1, pool.boundRequestCount());

            // When
            pool.unbindRequest(id);

            // Then
            assertEquals(freeBefore, pool.freeSlots());
            assertEquals(0, pool.boundRequestCount());
        }
    }

    @Test
    public void testGivenFragmentedFreeListWhenBindRequestThenAllocSucceeds() {
        // Given – four contiguous 64-slot binds fill a 256-slot pool (pageSize=16).
        // Unbinding the odd ones leaves 8 free pages in two 4-page holes — not
        // enough for a contiguous 6-page (96-slot) run, but enough in total.
        try (var pool = new KvCachePool(1, 256, 2, 16, 16, Device.CPU(), ScalarType.Float)) {
            pool.setPrefixReuseEnabled(false);
            int id0 = pool.bindRequest(new int[]{1, 2, 3, 4}, 64);
            int id1 = pool.bindRequest(new int[]{5, 6, 7, 8}, 64);
            int id2 = pool.bindRequest(new int[]{9, 10, 11, 12}, 64);
            int id3 = pool.bindRequest(new int[]{13, 14, 15, 16}, 64);
            pool.unbindRequest(id0);
            pool.unbindRequest(id2);
            assertEquals(128, pool.freeSlots());

            // When – allocate 96 slots across fragmented free pages
            int id = pool.bindRequest(new int[]{20, 21, 22, 23}, 96);

            // Then – bind succeeds; put/get still round-trip through the page table
            assertTrue(id > 0);
            assertEquals(32, pool.freeSlots());
            pool.activateStep(id);
            Tensor k = Tensor.ones(1, 4, 2, 16);
            Tensor v = Tensor.full(7.0f, 1, 4, 2, 16);
            try {
                pool.put(0, 0, k, v);
            } finally {
                k.close();
                v.close();
            }
            var cached = pool.get(0, 4);
            assertEquals(7.0f, cached._2().getFloat(0, 0, 0, 0), 1e-5);
            cached._1().close();
            cached._2().close();

            pool.unbindRequest(id);
            pool.unbindRequest(id1);
            pool.unbindRequest(id3);
            assertEquals(0, pool.boundRequestCount());
            assertEquals(256, pool.freeSlots());
        }
    }

    @Test
    public void testGivenRaggedLengthsWhenBuildFlashInferMetadataThenIndptrMatches() {
        // Given – two multi-request bindings with different capacities
        try (var pool = new KvCachePool(1, 128, 2, 16, 16, Device.CPU(), ScalarType.Float)) {
            pool.setPrefixReuseEnabled(false);
            int id1 = pool.bindRequest(new int[]{1, 2, 3, 4}, 32);
            int id2 = pool.bindRequest(new int[]{5, 6, 7, 8}, 48);
            pool.activateStep(id1, id2);

            // When
            try (var meta = pool.buildFlashInferMetadata(new int[]{10, 25})) {
                // Then – indptr[0]=0, pages for len 10 → 1 page (pageSize=16),
                // len 25 → 2 pages; indptr = [0, 1, 3]
                int[] indptr = meta.pagedKvIndptr().intArray();
                assertArrayEquals(new int[]{0, 1, 3}, indptr);
                assertEquals(2, meta.pagedKvLastPageLen().shape()[0]);
            }

            pool.unbindRequest(id1);
            pool.unbindRequest(id2);
        }
    }

    @Test
    public void testGivenRaggedPutWhenBuildMetadataThenLastPageLensMatchPositions() {
        // Given
        try (var pool = new KvCachePool(1, 128, 2, 16, 16, Device.CPU(), ScalarType.Float)) {
            pool.setPrefixReuseEnabled(false);
            int id1 = pool.bindRequest(new int[]{1, 2, 3, 4}, 64);
            int id2 = pool.bindRequest(new int[]{5, 6, 7, 8}, 64);
            pool.activateStep(id1, id2);

            int[] startPos = {9, 24};
            Tensor k = Tensor.ones(2, 1, 2, 16);
            Tensor v = Tensor.full(2.0f, 2, 1, 2, 16);
            pool.put(0, startPos, k, v);
            k.close();
            v.close();

            int[] cacheLens = {startPos[0] + 1, startPos[1] + 1}; // 10, 25
            try (var meta = pool.buildFlashInferMetadata(cacheLens)) {
                int[] indptr = meta.pagedKvIndptr().intArray();
                assertArrayEquals(new int[]{0, 1, 3}, indptr);
                int[] last = meta.pagedKvLastPageLen().intArray();
                // len 10 → last page 10; len 25 → last page 9 (25 % 16)
                assertEquals(10, last[0]);
                assertEquals(9, last[1]);
            }

            pool.unbindRequest(id1);
            pool.unbindRequest(id2);
        }
    }

    @Test
    public void testGivenBatchOneDecodeWhenLengthIncrementsWithinPageThenReusesMetadata() {
        try (var pool = new KvCachePool(1, 128, 2, 16, 16, Device.CPU(), ScalarType.Float)) {
            pool.setPrefixReuseEnabled(false);
            int id = pool.bindRequest(new int[]{1, 2, 3, 4}, 64);
            pool.activateStep(id);
            var len10 = pool.sharedFlashInferMetadata(10);
            var len10Again = pool.sharedFlashInferMetadata(10);
            assertSame(len10, len10Again);

            pool.activateStep(id);
            var len11 = pool.sharedFlashInferMetadata(11);
            assertSame(len10, len11, "within-page bump reuses CSR tensors");

            pool.unbindRequest(id);
        }
    }

    @Test
    public void testGivenActivateStepWhenSharedFlashInferMetadataThenReusesSameInstance() {
        try (var pool = new KvCachePool(1, 128, 2, 16, 16, Device.CPU(), ScalarType.Float)) {
            pool.setPrefixReuseEnabled(false);
            int id1 = pool.bindRequest(new int[]{1, 2, 3, 4}, 32);
            int id2 = pool.bindRequest(new int[]{5, 6, 7, 8}, 48);
            pool.activateStep(id1, id2);

            int[] cacheLens = {10, 25};
            var first = pool.sharedFlashInferMetadata(cacheLens);
            var second = pool.sharedFlashInferMetadata(cacheLens);
            assertSame(first, second);

            pool.activateStep(id1, id2);
            var afterReactivate = pool.sharedFlashInferMetadata(cacheLens);
            assertSame(first, afterReactivate, "same cohort activateStep preserves CSR metadata");

            pool.activateStep(id1);
            var singleBatch = pool.sharedFlashInferMetadata(10);
            assertNotSame(first, singleBatch, "cohort change rebuilds metadata");

            pool.unbindRequest(id1);
            pool.unbindRequest(id2);
        }
    }
}
