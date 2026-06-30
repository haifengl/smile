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

import java.util.ArrayList;

import org.junit.jupiter.api.*;
import smile.deep.tensor.Tensor;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RadixCache}.
 *
 * @author Haifeng Li
 */
public class RadixCacheTest {

    /** Creates a 1-D {@code int64} KV index tensor from a {@code long} vararg. */
    private static Tensor kv(long... indices) {
        return Tensor.of(indices);
    }

    // ===== Insert and match =====

    @Test
    public void testGivenEmptyCacheWhenInsertSingleSequenceThenMatchReturnsFullSequence() {
        // Given
        var cache = new RadixCache();
        var tokens = new int[]{1, 2, 3};

        // When
        try (var kv = kv(10L, 20L, 30L)) {
            cache.insert(tokens, kv);
        }

        // Then – MatchResult is AutoCloseable; tensor released at end of block
        try (var result = cache.matchPrefix(tokens)) {
            assertArrayEquals(new long[]{10L, 20L, 30L}, result.indices().longArray());
        }
        cache.reset();
    }

    @Test
    public void testGivenCacheWithPrefixWhenInsertLongerSequenceThenMatchReturnsCachedPrefix() {
        // Given
        var cache = new RadixCache();
        try (var kv = kv(10L, 20L, 30L)) {
            cache.insert(new int[]{1, 2, 3}, kv);
        }

        // When – insert a longer sequence sharing the same prefix
        InsertResult insertResult;
        try (var kv = kv(10L, 20L, 30L, 40L, 50L)) {
            insertResult = cache.insert(new int[]{1, 2, 3, 4, 5}, kv);
        }

        // Then – all 5 tokens should be cached after the second insert
        assertEquals(3, insertResult.prefixLen());
        try (var result = cache.matchPrefix(new int[]{1, 2, 3, 4, 5})) {
            assertArrayEquals(new long[]{10L, 20L, 30L, 40L, 50L}, result.indices().longArray());
        }
        cache.reset();
    }

    @Test
    public void testGivenCacheWhenInsertDivergingSequencesThenBothAreMatched() {
        // Given
        var cache = new RadixCache();
        try (var kv1 = kv(10L, 20L, 30L);
             var kv2 = kv(10L, 20L, 40L, 50L)) {
            cache.insert(new int[]{1, 2, 3}, kv1);
            cache.insert(new int[]{1, 2, 4, 5}, kv2);
        }

        // Then – seq1 is fully cached
        try (var r1 = cache.matchPrefix(new int[]{1, 2, 3})) {
            assertArrayEquals(new long[]{10L, 20L, 30L}, r1.indices().longArray());
        }

        // Then – seq2 is fully cached, sharing the first 2 tokens with seq1
        try (var r2 = cache.matchPrefix(new int[]{1, 2, 4, 5})) {
            assertArrayEquals(new long[]{10L, 20L, 40L, 50L}, r2.indices().longArray());
        }
        cache.reset();
    }

    @Test
    public void testGivenCacheWhenMatchUnknownSequenceThenReturnsEmpty() {
        // Given
        var cache = new RadixCache();
        try (var kv = kv(10L, 20L, 30L)) {
            cache.insert(new int[]{1, 2, 3}, kv);
        }

        // When
        try (var result = cache.matchPrefix(new int[]{9, 8, 7})) {
            // Then
            assertEquals(0, result.length());
            assertSame(cache.root, result.lastNode());
        }
        cache.reset();
    }

    @Test
    public void testGivenCacheWhenMatchPartialPrefixThenReturnsMatchedPortion() {
        // Given
        var cache = new RadixCache();
        try (var kv = kv(10L, 20L, 30L, 40L, 50L)) {
            cache.insert(new int[]{1, 2, 3, 4, 5}, kv);
        }

        // When – query shares first 3 tokens, then diverges
        try (var result = cache.matchPrefix(new int[]{1, 2, 3, 9, 9})) {
            // Then – only the first 3 matched tokens are returned
            assertEquals(3, result.length());
            assertArrayEquals(new long[]{10L, 20L, 30L}, result.indices().longArray());
        }
        cache.reset();
    }

    @Test
    public void testGivenCacheWhenInsertSameSequenceTwiceThenPrefixLenIsFullLength() {
        // Given
        var cache = new RadixCache();
        try (var kv = kv(10L, 20L, 30L)) {
            cache.insert(new int[]{1, 2, 3}, kv);
        }

        // When
        InsertResult result;
        try (var kv = kv(10L, 20L, 30L)) {
            result = cache.insert(new int[]{1, 2, 3}, kv);
        }

        // Then – full sequence was already cached; no new leaf created
        assertEquals(3, result.prefixLen());
        cache.reset();
    }

    // ===== Total size and reset =====

    @Test
    public void testGivenCacheAfterMultipleInsertsThenTotalSizeIsCorrect() {
        // Given
        var cache = new RadixCache();

        // When
        try (var t1 = kv(10L, 20L, 30L);
             var t2 = kv(10L, 20L, 40L, 50L);
             var t3 = kv(10L, 20L, 40L, 50L, 60L, 70L);
             var t4 = kv(80L, 90L, 100L)) {
            cache.insert(new int[]{1, 2, 3}, t1);
            cache.insert(new int[]{1, 2, 4, 5}, t2);
            cache.insert(new int[]{1, 2, 4, 5, 6, 7}, t3);
            cache.insert(new int[]{8, 9, 10}, t4);
        }

        // Then – shared prefix {1,2} is stored once; total unique tokens = 2 + 1 + 2 + 2 + 3 = 10
        assertEquals(10, cache.totalSize());
        cache.reset();
    }

    @Test
    public void testGivenCacheWhenResetThenTotalSizeIsZero() {
        // Given
        var cache = new RadixCache();
        try (var kv = kv(10L, 20L, 30L)) {
            cache.insert(new int[]{1, 2, 3}, kv);
        }

        // When
        cache.reset();

        // Then
        assertEquals(0, cache.totalSize());
        assertEquals(0, cache.evictableSize());
        assertEquals(0, cache.protectedSize());
    }

    // ===== Eviction =====

    @Test
    public void testGivenCacheWhenEvictThenKvIndicesAreFreedAndSizeDecreases() {
        // Given
        var cache = new RadixCache();
        try (var t1 = kv(10L, 20L, 30L);
             var t2 = kv(40L, 50L, 60L)) {
            cache.insert(new int[]{1, 2, 3}, t1);
            cache.insert(new int[]{4, 5, 6}, t2);
        }
        assertEquals(6, cache.evictableSize());

        // When
        var freed = new ArrayList<long[]>();
        var numEvicted = cache.evict(3, t -> {
            freed.add(t.longArray());
            t.close();
        });

        // Then – at least 3 tokens evicted
        assertTrue(numEvicted >= 3);
        assertFalse(freed.isEmpty());
        assertTrue(cache.evictableSize() < 6);
        cache.reset();
    }

    @Test
    public void testGivenLockedNodeWhenEvictThenLockedNodeIsNotEvicted() {
        // Given
        var cache = new RadixCache();
        try (var t1 = kv(10L, 20L, 30L);
             var t2 = kv(40L, 50L, 60L)) {
            cache.insert(new int[]{1, 2, 3}, t1);
            cache.insert(new int[]{4, 5, 6}, t2);
        }

        RadixTreeNode lockedNode;
        try (var locked = cache.matchPrefix(new int[]{1, 2, 3})) {
            lockedNode = locked.lastNode();
            cache.incLockRef(lockedNode);
        }

        // When – try to evict 6 tokens (entire cache)
        cache.evict(6, Tensor::close);

        // Then – locked sequence is preserved
        try (var afterEvict = cache.matchPrefix(new int[]{1, 2, 3})) {
            assertEquals(3, afterEvict.length());
        }

        cache.decLockRef(lockedNode);
        cache.reset();
    }

    @Test
    public void testGivenLockedAndUnlockedNodesWhenDecLockRefThenNodeBecomesEvictable() {
        // Given
        var cache = new RadixCache();
        try (var kv = kv(10L, 20L, 30L)) {
            cache.insert(new int[]{1, 2, 3}, kv);
        }

        RadixTreeNode node;
        try (var result = cache.matchPrefix(new int[]{1, 2, 3})) {
            node = result.lastNode();
        }

        var beforeLock = cache.evictableSize();
        cache.incLockRef(node);
        assertEquals(0, cache.evictableSize());

        // When
        cache.decLockRef(node);

        // Then
        assertEquals(beforeLock, cache.evictableSize());
        cache.reset();
    }

    // ===== Evictable size tracking =====

    @Test
    public void testGivenNewCacheWhenInsertThenEvictableSizeEqualsTotalSize() {
        // Given
        var cache = new RadixCache();

        // When
        try (var kv = kv(10L, 20L, 30L)) {
            cache.insert(new int[]{1, 2, 3}, kv);
        }

        // Then
        assertEquals(cache.totalSize(), cache.evictableSize());
        assertEquals(0, cache.protectedSize());
        cache.reset();
    }

    @Test
    public void testGivenInsertedSequenceWhenIncLockRefThenEvictableSizeDecreasesAndProtectedIncreases() {
        // Given
        var cache = new RadixCache();
        try (var kv = kv(10L, 20L, 30L)) {
            cache.insert(new int[]{1, 2, 3}, kv);
        }

        RadixTreeNode node;
        try (var result = cache.matchPrefix(new int[]{1, 2, 3})) {
            node = result.lastNode();
        }

        // When
        cache.incLockRef(node);

        // Then
        assertEquals(0, cache.evictableSize());
        assertEquals(3, cache.protectedSize());

        cache.decLockRef(node);
        cache.reset();
    }

    // ===== Page size support =====

    @Test
    public void testGivenPageSizeOfTwoWhenInsertOddLengthSequenceThenTailIsDropped() {
        // Given
        var cache = new RadixCache(2);

        // When – 5 tokens: only 4 are page-aligned
        try (var kv = kv(10L, 20L, 30L, 40L, 50L)) {
            cache.insert(new int[]{1, 2, 3, 4, 5}, kv);
        }

        // Then – 4 tokens cached (page-aligned)
        try (var result = cache.matchPrefix(new int[]{1, 2, 3, 4, 5})) {
            assertEquals(4, result.length());
        }
        cache.reset();
    }

    @Test
    public void testGivenPageSizeOfTwoWhenMatchSharedPrefixThenResultIsPageAligned() {
        // Given
        var cache = new RadixCache(2);
        try (var kv = kv(10L, 20L, 30L, 40L, 50L, 60L)) {
            cache.insert(new int[]{1, 2, 3, 4, 5, 6}, kv);
        }

        // When – query matches first 3 tokens of a page-size-2 cache;
        // only 2 tokens (first full page) should be returned.
        try (var result = cache.matchPrefix(new int[]{1, 2, 3, 9})) {
            // Then
            assertEquals(2, result.length());
            assertArrayEquals(new long[]{10L, 20L}, result.indices().longArray());
        }
        cache.reset();
    }

    // ===== Namespace isolation =====

    @Test
    public void testGivenDifferentExtraKeysThenNamespacesAreIsolated() {
        // Given
        var cache = new RadixCache();
        var tokens = new int[]{1, 2, 3};
        try (var kvA = kv(10L, 20L, 30L);
             var kvB = kv(100L, 200L, 300L)) {
            cache.insert(tokens, kvA, "lora-a", 0);
            cache.insert(tokens, kvB, "lora-b", 0);
        }

        // Then – each namespace returns its own KV indices
        try (var ra = cache.matchPrefix(tokens, "lora-a")) {
            assertArrayEquals(new long[]{10L, 20L, 30L}, ra.indices().longArray());
        }
        try (var rb = cache.matchPrefix(tokens, "lora-b")) {
            assertArrayEquals(new long[]{100L, 200L, 300L}, rb.indices().longArray());
        }
        cache.reset();
    }

    // ===== Node splitting =====

    @Test
    public void testGivenLongCachedSequenceWhenMatchPartialThenNodeIsSplitAndRematched() {
        // Given
        var cache = new RadixCache();
        try (var kv = kv(10L, 20L, 30L, 40L, 50L)) {
            cache.insert(new int[]{1, 2, 3, 4, 5}, kv);
        }

        // When – first match triggers a split at position 3
        long[] first;
        try (var r1 = cache.matchPrefix(new int[]{1, 2, 3, 9, 9})) {
            assertEquals(3, r1.length());
            first = r1.indices().longArray();
        }

        // When – second match reuses the split node
        try (var r2 = cache.matchPrefix(new int[]{1, 2, 3, 9, 9})) {
            // Then – result is stable across multiple queries
            assertEquals(3, r2.length());
            assertArrayEquals(first, r2.indices().longArray());
        }
        cache.reset();
    }

    @Test
    public void testGivenCacheWhenMatchEmptyKeyThenReturnsRootAndEmptyIndices() {
        // Given
        var cache = new RadixCache();
        try (var kv = kv(10L, 20L, 30L)) {
            cache.insert(new int[]{1, 2, 3}, kv);
        }

        // When
        try (var result = cache.matchPrefix(new int[0])) {
            // Then
            assertEquals(0, result.length());
            assertSame(cache.root, result.lastNode());
        }
        cache.reset();
    }
}
