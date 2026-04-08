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

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IntDoubleHashMapTest {

    @Test
    public void testPutAndGet() {
        IntDoubleHashMap map = new IntDoubleHashMap();
        assertTrue(map.isEmpty());
        map.put(1, 2.5);
        map.put(2, 3.5);
        assertFalse(map.isEmpty());
        assertEquals(2, map.size());
        assertEquals(2.5, map.get(1), 1e-15);
        assertEquals(3.5, map.get(2), 1e-15);
    }

    @Test
    public void testGetMissingKey() {
        IntDoubleHashMap map = new IntDoubleHashMap();
        assertTrue(Double.isNaN(map.get(42)));
    }

    @Test
    public void testUpdateExistingKey() {
        IntDoubleHashMap map = new IntDoubleHashMap();
        double old = map.put(5, 1.0);
        assertTrue(Double.isNaN(old));
        double prev = map.put(5, 2.0);
        assertEquals(1.0, prev, 1e-15);
        assertEquals(2.0, map.get(5), 1e-15);
        assertEquals(1, map.size());
    }

    @Test
    public void testRemove() {
        IntDoubleHashMap map = new IntDoubleHashMap();
        map.put(1, 10.0);
        map.put(2, 20.0);
        assertEquals(10.0, map.remove(1), 1e-15);
        assertEquals(1, map.size());
        assertTrue(Double.isNaN(map.get(1)));
        assertEquals(20.0, map.get(2), 1e-15);
    }

    @Test
    public void testRemoveMissingKey() {
        IntDoubleHashMap map = new IntDoubleHashMap();
        assertTrue(Double.isNaN(map.remove(99)));
    }

    /**
     * Probe-chain correctness: after removing a key that sits between two
     * colliding keys in the linear-probe sequence, the second key must still
     * be reachable.
     *
     * We force a collision by choosing keys whose hash(k) values land on the
     * same slot.  With capacity=2 (smallest power-of-two ≥ ceil(1/0.75)=2)
     * mask=1, so hash(k) = (k*PHI ^ (k*PHI>>>16)) & 1.  Rather than
     * computing exact collisions, we just insert many keys and remove one
     * from the middle – if the fix works every remaining key is still
     * reachable.
     */
    @Test
    public void testRemovePreservesProbeChain() {
        IntDoubleHashMap map = new IntDoubleHashMap(4, 0.75f);
        // Insert 10 keys; several will hash to the same initial slot.
        for (int i = 1; i <= 10; i++) map.put(i, i * 1.5);
        // Remove keys in reverse; each lookup for the remaining ones must succeed.
        for (int del = 10; del >= 1; del--) {
            assertEquals(del * 1.5, map.remove(del), 1e-12,
                    "remove(" + del + ") returned wrong value");
            for (int j = 1; j < del; j++) {
                assertEquals(j * 1.5, map.get(j), 1e-12,
                        "get(" + j + ") failed after removing " + del);
            }
        }
        assertTrue(map.isEmpty());
    }

    @Test
    public void testRehash() {
        IntDoubleHashMap map = new IntDoubleHashMap(2, 0.75f);
        for (int i = 1; i <= 100; i++) map.put(i, i);
        assertEquals(100, map.size());
        for (int i = 1; i <= 100; i++) {
            assertEquals(i, map.get(i), 1e-15);
        }
    }

    @Test
    public void testForbiddenKey() {
        IntDoubleHashMap map = new IntDoubleHashMap();
        assertThrows(IllegalArgumentException.class, () -> map.get(Integer.MIN_VALUE));
        assertThrows(IllegalArgumentException.class, () -> map.put(Integer.MIN_VALUE, 1.0));
        assertThrows(IllegalArgumentException.class, () -> map.remove(Integer.MIN_VALUE));
    }

    @Test
    public void testInvalidConstructorArgs() {
        assertThrows(IllegalArgumentException.class, () -> new IntDoubleHashMap(0, 0.75f));
        assertThrows(IllegalArgumentException.class, () -> new IntDoubleHashMap(16, 0.0f));
        assertThrows(IllegalArgumentException.class, () -> new IntDoubleHashMap(16, 1.0f));
    }
}
