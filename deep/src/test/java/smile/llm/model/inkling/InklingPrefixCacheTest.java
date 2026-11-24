/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.model.inkling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link InklingPrefixCache} with stand-in payloads (no GPU): longest-prefix lookup, pinning, LRU eviction, the snapshot
 * schedule of the sharded model.
 *
 * @author Haifeng Li
 */
public class InklingPrefixCacheTest {
    private static final class Fake implements InklingPrefixCache.Payload {
        final long bytes;
        boolean closed;

        Fake(long bytes) {
            this.bytes = bytes;
        }

        @Override
        public long bytes() {
            return bytes;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static int[] tokens(int n) {
        int[] t = new int[n];
        for (int i = 0; i < n; i++) {
            t[i] = i * 7 + 1;
        }
        return t;
    }

    @Test
    public void testGivenStoredPrefixesWhenAcquiredThenTheLongestOneNotBeyondTheLimitMatches() {
        InklingPrefixCache cache = new InklingPrefixCache(1000);
        cache.put(tokens(10), new Fake(100));
        cache.put(tokens(20), new Fake(100));
        int[] prompt = tokens(30);
        assertEquals(20, cache.acquire(prompt, 29).length());
        assertEquals(10, cache.acquire(prompt, 15).length());
        assertNull(cache.acquire(prompt, 9));
        int[] other = tokens(30);
        other[5] = -1;                      // diverges inside the stored prefixes
        assertNull(cache.acquire(other, 29));
    }

    @Test
    public void testGivenFullCacheWhenStoringThenLeastRecentUnpinnedEntryIsEvictedAndClosed() {
        InklingPrefixCache cache = new InklingPrefixCache(250);
        Fake a = new Fake(100), b = new Fake(100), c = new Fake(100);
        cache.put(tokens(10), a);
        cache.put(tokens(20), b);
        InklingPrefixCache.Entry pinned = cache.acquire(tokens(15), 14);      // pins the 10-token entry (and refreshes it)
        assertEquals(10, pinned.length());
        cache.put(tokens(30), c);          // does not fit: the 20-token entry goes, the pinned one stays
        assertTrue(b.closed);
        assertFalse(a.closed);
        assertFalse(cache.has(tokens(20), 20));
        assertTrue(cache.has(tokens(10), 10));
        assertTrue(cache.has(tokens(30), 30));
        cache.release(pinned);
        cache.clear();
        assertTrue(a.closed && c.closed);
    }

    @Test
    public void testGivenTooBigPayloadWhenStoredThenItIsClosedAndNotKept() {
        InklingPrefixCache cache = new InklingPrefixCache(50);
        Fake big = new Fake(100);
        assertFalse(cache.fits(100));
        cache.put(tokens(10), big);
        assertTrue(big.closed);
        assertFalse(cache.has(tokens(10), 10));
    }

    @Test
    public void testGivenShardedModelWhenSnapshotsAreScheduledThenTheyAreSparse() {
        assertEquals(1024, InklingTp.nextSnapshot(0));
        assertEquals(2048, InklingTp.nextSnapshot(1024));
        assertEquals(4096, InklingTp.nextSnapshot(2048));
        assertEquals(8192, InklingTp.nextSnapshot(4096));
        assertEquals(8192, InklingTp.nextSnapshot(5000));
        assertTrue(InklingTp.isSnapshotPosition(1024) && InklingTp.isSnapshotPosition(2048) && InklingTp.isSnapshotPosition(4096));
        assertTrue(InklingTp.isSnapshotPosition(8192) && InklingTp.isSnapshotPosition(32768));
        assertFalse(InklingTp.isSnapshotPosition(3072) || InklingTp.isSnapshotPosition(5000));
    }
}
