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

/**
 * Tests for IntPair, Tuple2, and Index.
 */
public class TupleAndIndexTest {

    // -------------------------------------------------------------------------
    // IntPair
    // -------------------------------------------------------------------------

    @Test
    public void testIntPairAccessors() {
        IntPair p = new IntPair(3, 7);
        assertEquals(3, p._1());
        assertEquals(7, p._2());
    }

    @Test
    public void testIntPairEquality() {
        IntPair a = new IntPair(1, 2);
        IntPair b = new IntPair(1, 2);
        IntPair c = new IntPair(1, 3);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    public void testIntPairToString() {
        IntPair p = new IntPair(4, 5);
        String s = p.toString();
        assertTrue(s.contains("4"));
        assertTrue(s.contains("5"));
    }

    // -------------------------------------------------------------------------
    // Tuple2
    // -------------------------------------------------------------------------

    @Test
    public void testTuple2Accessors() {
        Tuple2<String, Integer> t = new Tuple2<>("hello", 42);
        assertEquals("hello", t._1());
        assertEquals(42, t._2());
    }

    @Test
    public void testTuple2Equality() {
        Tuple2<String, Integer> a = new Tuple2<>("x", 1);
        Tuple2<String, Integer> b = new Tuple2<>("x", 1);
        Tuple2<String, Integer> c = new Tuple2<>("y", 1);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    public void testTuple2NullElements() {
        Tuple2<String, Integer> t = new Tuple2<>(null, null);
        assertNull(t._1());
        assertNull(t._2());
    }

    // -------------------------------------------------------------------------
    // Index
    // -------------------------------------------------------------------------

    @Test
    public void testIndexOf() {
        Index idx = Index.of(2, 5, 8);
        assertEquals(3, idx.size());
        assertEquals(2, idx.apply(0));
        assertEquals(5, idx.apply(1));
        assertEquals(8, idx.apply(2));
        assertArrayEquals(new int[]{2, 5, 8}, idx.toArray());
    }

    @Test
    public void testIndexOfMask() {
        Index idx = Index.of(true, false, true, false, true);
        assertEquals(3, idx.size());
        assertArrayEquals(new int[]{0, 2, 4}, idx.toArray());
    }

    @Test
    public void testIndexRange() {
        Index idx = Index.range(3, 7);
        assertEquals(4, idx.size());
        assertEquals(3, idx.apply(0));
        assertEquals(6, idx.apply(3));
        assertArrayEquals(new int[]{3, 4, 5, 6}, idx.toArray());
    }

    @Test
    public void testIndexRangeWithStep() {
        Index idx = Index.range(0, 10, 2);
        assertArrayEquals(new int[]{0, 2, 4, 6, 8}, idx.toArray());
    }

    @Test
    public void testIndexStream() {
        Index idx = Index.of(1, 3, 5);
        assertEquals(9, idx.stream().sum());
    }

    @Test
    public void testIndexFlatten() {
        Index parent = Index.of(10, 20, 30, 40);
        Index child  = Index.of(0, 2);          // select parent[0]=10 and parent[2]=30
        Index flat   = parent.flatten(child);
        assertArrayEquals(new int[]{10, 30}, flat.toArray());
    }
}

