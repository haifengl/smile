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
import java.util.List;
import org.junit.jupiter.api.*;
import smile.deep.tensor.Tensor;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RadixCache}.
 *
 * @author Haifeng Li
 */
public class RadixCacheTest {

    /** Helper to create a 1-D int64 KV index tensor from a long vararg. */
    private static Tensor kv(long... indices) {
        return Tensor.of(indices);
    }

    /** Helper to extract a long[] from a MatchResult and close the tensor. */
    private static long[] toArray(MatchResult result) {
        long[] arr = result.indices().longArray();
        result.indices().close();
        return arr;
    }

    // ===== Insert and match =====

    @Test
    public void testGivenEmptyCacheWhenInsertSingleSequenceThenMatchReturnsFullSequence() {
        // Given
        RadixCache cache = new RadixCache();
        int[] tokens = {1, 2, 3};

        // When
        try (Tensor kv = kv(10L, 20L, 30L)) {
            cache.insert(tokens, kv);
        }
        MatchResult result = cache.matchPrefix(tokens);

        // Then
        assertArrayEquals(new long[]{10L, 20L, 30L}, toArray(result));
        cache.reset();
    }

    @Test
    public void testGivenCacheWithPrefixWhenInsertLongerSequenceThenMatchReturnsCachedPrefix() {
        // Given
        RadixCache cache = new RadixCache();
        try (Tensor kv = kv(10L, 20L, 30L)) {
            cache.insert(new int[]{1, 2, 3}, kv);
        }

        // When – insert a longer sequence sharing the same prefix
        InsertResult insertResult;
        try (Tensor kv = kv(10L, 20L, 30L, 40L, 50L)) {
            insertResult = cache.insert(new int[]{1, 2, 3, 4, 5}, kv);
        }
        MatchResult matchResult = cache.matchPrefix(new int[]{1, 2, 3, 4, 5});

        // Then – all 5 tokens should be cached after the second insert
        assertEquals(3, insertResult.prefixLen());
        assertArrayEquals(new long[]{10L, 20L, 30L, 40L, 50L}, toArray(matchResult));
        cache.reset();
    }

    @Test
    public void testGivenCacheWhenInsertDivergingSequencesThenBothAreMatched() {
        // Given
        RadixCache cache = new RadixCache();
        try (Tensor kv1 = kv(10L, 20L, 30L);
             Tensor kv2 = kv(10L, 20L, 40L, 50L)) {
            cache.insert(new int[]{1, 2, 3}, kv1);
            cache.insert(new int[]{1, 2, 4, 5}, kv2);
        }

        // Then – seq1 is fully cached
        MatchResult r1 = cache.matchPrefix(new int[]{1, 2, 3});
        assertArrayEquals(new long[]{10L, 20L, 30L}, toArray(r1));

        // Then – seq2 is fully cached, sharing the first 2 tokens with seq1
        MatchResult r2 = cache.matchPrefix(new int[]{1, 2, 4, 5});
        assertArrayEquals(new long[]{10L, 20L, 40L, 50L}, toArray(r2));
        cache.reset();
    }

    @Test
    public void testGivenCacheWhenMatchUnknownSequenceThenReturnsEmpty() {
        // Given
        RadixCache cache = new RadixCache();
        try (Tensor kv = kv(10L, 20L, 30L)) {
            cache.insert(new int[]{1, 2, 3}, kv);
        }

        // When
        MatchResult result = cache.matchPrefix(new int[]{9, 8, 7});

        // Then
        assertEquals(0, result.length());
        assertSame(cache.root, result.lastNode());
        result.indices().close();
        cache.reset();
    }

    @Test
    public void testGivenCacheWhenMatchPartialPrefixThenReturnsMatchedPortion() {
        // Given
        RadixCache cache = new RadixCache();
        try (Tensor kv = kv(10L, 20L, 30L, 40L, 50L)) {
            cache.insert(new int[]{1, 2, 3, 4, 5}, kv);
        }

        // When – query shares first 3 tokens, then diverges
        MatchResult result = cache.matchPrefix(new int[]{1, 2, 3, 9, 9});

        // Then – only the first 3 matched tokens are returned
        assertEquals(3, result.length());
        assertArrayEquals(new long[]{10L, 20L, 30L}, toArray(result));
        cache.reset();
    }

    @Test
    public void testGivenCacheWhenInsertSameSequenceTwiceThenPrefixLenIsFullLength() {
        // Given
        RadixCache cache = new RadixCache();
        try (Tensor kv = kv(10L, 20L, 30L)) {
            cache.insert(new int[]{1, 2, 3}, kv);
        }

        // When
        InsertResult result;
        try (Tensor kv = kv(10L, 20L, 30L)) {
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
        RadixCache cache = new RadixCache();

        // When
        try (Tensor t1 = kv(10L, 20L, 30L);
             Tensor t2 = kv(10L, 20L, 40L, 50L);
             Tensor t3 = kv(10L, 20L, 40L, 50L, 60L, 70L);
             Tensor t4 = kv(80L, 90L, 100L)) {
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
        RadixCache cache = new RadixCache();
        try (Tensor kv = kv(10L, 20L, 30L)) {
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
        RadixCache cache = new RadixCache();
        try (Tensor t1 = kv(10L, 20L, 30L);
             Tensor t2 = kv(40L, 50L, 60L)) {
            cache.insert(new int[]{1, 2, 3}, t1);
            cache.insert(new int[]{4, 5, 6}, t2);
        }
        assertEquals(6, cache.evictableSize());

        // When
        List<long[]> freed = new ArrayList<>();
        int numEvicted = cache.evict(3, t -> {
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
        RadixCache cache = new RadixCache();
        try (Tensor t1 = kv(10L, 20L, 30L);
             Tensor t2 = kv(40L, 50L, 60L)) {
            cache.insert(new int[]{1, 2, 3}, t1);
            cache.insert(new int[]{4, 5, 6}, t2);
        }

        MatchResult locked = cache.matchPrefix(new int[]{1, 2, 3});
        cache.incLockRef(locked.lastNode());
        locked.indices().close();

        // When – try to evict 6 tokens (entire cache)
        cache.evict(6, Tensor::close);

        // Then – locked sequence is preserved
        MatchResult afterEvict = cache.matchPrefix(new int[]{1, 2, 3});
        assertEquals(3, afterEvict.length());
        afterEvict.indices().close();

        cache.decLockRef(locked.lastNode());
        cache.reset();
    }

    @Test
    public void testGivenLockedAndUnlockedNodesWhenDecLockRefThenNodeBecomesEvictable() {
        // Given
        RadixCache cache = new RadixCache();
        try (Tensor kv = kv(10L, 20L, 30L)) {
            cache.insert(new int[]{1, 2, 3}, kv);
        }

        MatchResult result = cache.matchPrefix(new int[]{1, 2, 3});
        TreeNode node = result.lastNode();
        result.indices().close();

        int beforeLock = cache.evictableSize();
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
        RadixCache cache = new RadixCache();

        // When
        try (Tensor kv = kv(10L, 20L, 30L)) {
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
        RadixCache cache = new RadixCache();
        try (Tensor kv = kv(10L, 20L, 30L)) {
            cache.insert(new int[]{1, 2, 3}, kv);
        }
        MatchResult result = cache.matchPrefix(new int[]{1, 2, 3});
        result.indices().close();

        // When
        cache.incLockRef(result.lastNode());

        // Then
        assertEquals(0, cache.evictableSize());
        assertEquals(3, cache.protectedSize());

        cache.decLockRef(result.lastNode());
        cache.reset();
    }

    // ===== Page size support =====

    @Test
    public void testGivenPageSizeOfTwoWhenInsertOddLengthSequenceThenTailIsDropped() {
        // Given
        RadixCache cache = new RadixCache(2);

        // When – 5 tokens: only 4 are page-aligned
        try (Tensor kv = kv(10L, 20L, 30L, 40L, 50L)) {
            cache.insert(new int[]{1, 2, 3, 4, 5}, kv);
        }
        MatchResult result = cache.matchPrefix(new int[]{1, 2, 3, 4, 5});

        // Then – 4 tokens cached (page-aligned)
        assertEquals(4, result.length());
        result.indices().close();
        cache.reset();
    }

    @Test
    public void testGivenPageSizeOfTwoWhenMatchSharedPrefixThenResultIsPageAligned() {
        // Given
        RadixCache cache = new RadixCache(2);
        try (Tensor kv = kv(10L, 20L, 30L, 40L, 50L, 60L)) {
            cache.insert(new int[]{1, 2, 3, 4, 5, 6}, kv);
        }

        // When – query matches first 3 tokens of a page-size-2 cache;
        // only 2 tokens (first full page) should be returned.
        MatchResult result = cache.matchPrefix(new int[]{1, 2, 3, 9});

        // Then
        assertEquals(2, result.length());
        assertArrayEquals(new long[]{10L, 20L}, toArray(result));
        cache.reset();
    }

    // ===== Namespace isolation =====

    @Test
    public void testGivenDifferentExtraKeysThenNamespacesAreIsolated() {
        // Given
        RadixCache cache = new RadixCache();
        int[] tokens = {1, 2, 3};
        try (Tensor kvA = kv(10L, 20L, 30L);
             Tensor kvB = kv(100L, 200L, 300L)) {
            cache.insert(tokens, kvA, "lora-a", 0);
            cache.insert(tokens, kvB, "lora-b", 0);
        }

        // When
        MatchResult ra = cache.matchPrefix(tokens, "lora-a");
        MatchResult rb = cache.matchPrefix(tokens, "lora-b");

        // Then – each namespace returns its own KV indices
        assertArrayEquals(new long[]{10L, 20L, 30L}, toArray(ra));
        assertArrayEquals(new long[]{100L, 200L, 300L}, toArray(rb));
        cache.reset();
    }

    // ===== Node splitting =====

    @Test
    public void testGivenLongCachedSequenceWhenMatchPartialThenNodeIsSplitAndRematched() {
        // Given
        RadixCache cache = new RadixCache();
        try (Tensor kv = kv(10L, 20L, 30L, 40L, 50L)) {
            cache.insert(new int[]{1, 2, 3, 4, 5}, kv);
        }

        // When – first match triggers a split at position 3
        MatchResult r1 = cache.matchPrefix(new int[]{1, 2, 3, 9, 9});
        assertEquals(3, r1.length());
        long[] first = toArray(r1);

        // When – second match reuses the split node
        MatchResult r2 = cache.matchPrefix(new int[]{1, 2, 3, 9, 9});

        // Then – result is stable across multiple queries
        assertEquals(3, r2.length());
        assertArrayEquals(first, toArray(r2));
        cache.reset();
    }

    @Test
    public void testGivenCacheWhenMatchEmptyKeyThenReturnsRootAndEmptyIndices() {
        // Given
        RadixCache cache = new RadixCache();
        try (Tensor kv = kv(10L, 20L, 30L)) {
            cache.insert(new int[]{1, 2, 3}, kv);
        }

        // When
        MatchResult result = cache.matchPrefix(new int[0]);

        // Then
        assertEquals(0, result.length());
        assertSame(cache.root, result.lastNode());
        result.indices().close();
        cache.reset();
    }
}
