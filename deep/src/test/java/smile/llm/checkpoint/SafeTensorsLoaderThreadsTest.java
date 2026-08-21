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
package smile.llm.checkpoint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SafeTensorsLoaderThreads#resolve}.
 */
public class SafeTensorsLoaderThreadsTest {

    @Test
    public void testGivenZeroShardsWhenResolveThenZero() {
        assertEquals(0, SafeTensorsLoaderThreads.resolve(0, 0));
        assertEquals(0, SafeTensorsLoaderThreads.resolve(8, 0));
    }

    @Test
    public void testGivenAutoWhenOneShardThenOne() {
        assertEquals(1, SafeTensorsLoaderThreads.resolve(0, 1));
    }

    @Test
    public void testGivenConfiguredExceedsShardsWhenResolveThenCappedByShards() {
        assertEquals(3, SafeTensorsLoaderThreads.resolve(64, 3));
    }

    @Test
    public void testGivenConfiguredWhenResolveThenHonorsConfigured() {
        assertEquals(2, SafeTensorsLoaderThreads.resolve(2, 100));
        assertEquals(1, SafeTensorsLoaderThreads.resolve(1, 100));
    }

    @Test
    public void testGivenNegativeConfiguredWhenResolveThenThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> SafeTensorsLoaderThreads.resolve(-1, 4));
    }

    @Test
    public void testGivenNegativeShardsWhenResolveThenThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> SafeTensorsLoaderThreads.resolve(0, -1));
    }

    @Test
    public void testGivenAutoWhenManyShardsThenAtMostAutoCap() {
        int resolved = SafeTensorsLoaderThreads.resolve(0, 1000);
        int procs = Math.max(1, Runtime.getRuntime().availableProcessors());
        int auto = Math.min(SafeTensorsLoaderThreads.AUTO_CAP, procs);
        assertEquals(auto, resolved);
        assertTrue(resolved <= SafeTensorsLoaderThreads.AUTO_CAP);
    }
}
