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
package smile.util;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class IntHashSetTest {

    @Test
    public void testAddContains() {
        IntHashSet set = new IntHashSet();
        assertTrue(set.isEmpty());
        assertTrue(set.add(1));
        assertTrue(set.add(2));
        assertTrue(set.add(3));
        assertEquals(3, set.size());
        assertFalse(set.isEmpty());
        assertTrue(set.contains(1));
        assertTrue(set.contains(2));
        assertTrue(set.contains(3));
        assertFalse(set.contains(99));
    }

    @Test
    public void testAddDuplicate() {
        IntHashSet set = new IntHashSet();
        assertTrue(set.add(5));
        assertFalse(set.add(5));   // already present
        assertEquals(1, set.size());
    }

    @Test
    public void testRemove() {
        IntHashSet set = new IntHashSet();
        set.add(1); set.add(2); set.add(3);
        assertTrue(set.remove(2));
        assertFalse(set.contains(2));
        assertEquals(2, set.size());
        assertFalse(set.remove(99)); // not present
    }

    /**
     * Probe-chain correctness after remove: keys sharing a probe chain must
     * still be findable after an intermediate element is deleted.
     */
    @Test
    public void testRemovePreservesProbeChain() {
        IntHashSet set = new IntHashSet(4, 0.75f);
        for (int i = 1; i <= 15; i++) set.add(i);
        for (int del = 15; del >= 1; del--) {
            assertTrue(set.remove(del), "remove(" + del + ") should return true");
            for (int j = 1; j < del; j++) {
                assertTrue(set.contains(j),
                        "contains(" + j + ") failed after removing " + del);
            }
        }
        assertTrue(set.isEmpty());
    }

    @Test
    public void testToArray() {
        IntHashSet set = new IntHashSet();
        set.add(10); set.add(20); set.add(30);
        int[] arr = set.toArray();
        Arrays.sort(arr);
        assertArrayEquals(new int[]{10, 20, 30}, arr);
    }

    @Test
    public void testRehash() {
        IntHashSet set = new IntHashSet(2, 0.75f);
        for (int i = 1; i <= 200; i++) set.add(i);
        assertEquals(200, set.size());
        for (int i = 1; i <= 200; i++) assertTrue(set.contains(i));
    }

    @Test
    public void testForbiddenKey() {
        IntHashSet set = new IntHashSet();
        assertThrows(IllegalArgumentException.class, () -> set.add(Integer.MIN_VALUE));
        assertThrows(IllegalArgumentException.class, () -> set.contains(Integer.MIN_VALUE));
        assertThrows(IllegalArgumentException.class, () -> set.remove(Integer.MIN_VALUE));
    }

    @Test
    public void testInvalidConstructorArgs() {
        assertThrows(IllegalArgumentException.class, () -> new IntHashSet(0, 0.75f));
        assertThrows(IllegalArgumentException.class, () -> new IntHashSet(16, 0.0f));
        assertThrows(IllegalArgumentException.class, () -> new IntHashSet(16, 1.0f));
    }
}

