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
package smile.neighbor;

import java.util.Arrays;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Neighbor} record.
 *
 * @author Haifeng Li
 */
public class NeighborTest {

    @Test
    public void testRecordFields() {
        double[] key = {1.0, 2.0};
        String value = "point";
        Neighbor<double[], String> n = new Neighbor<>(key, value, 5, 3.14);
        assertArrayEquals(new double[]{1.0, 2.0}, n.key());
        assertEquals("point", n.value());
        assertEquals(5, n.index());
        assertEquals(3.14, n.distance(), 1e-10);
    }

    @Test
    public void testOfFactory() {
        double[] key = {1.0, 2.0};
        Neighbor<double[], double[]> n = Neighbor.of(key, 3, 2.5);
        assertSame(key, n.key());
        assertSame(key, n.value());
        assertEquals(3, n.index());
        assertEquals(2.5, n.distance(), 1e-10);
    }

    @Test
    public void testCompareToByDistance() {
        Neighbor<String, String> near = new Neighbor<>("a", "a", 0, 1.0);
        Neighbor<String, String> far  = new Neighbor<>("b", "b", 1, 2.0);
        assertTrue(near.compareTo(far) < 0);
        assertTrue(far.compareTo(near) > 0);
    }

    @Test
    public void testCompareToByIndexOnTie() {
        // Same distance → sort by index
        Neighbor<String, String> first  = new Neighbor<>("a", "a", 0, 1.0);
        Neighbor<String, String> second = new Neighbor<>("b", "b", 1, 1.0);
        assertTrue(first.compareTo(second) < 0);
        assertTrue(second.compareTo(first) > 0);
    }

    @Test
    public void testCompareToEqual() {
        Neighbor<String, String> n1 = new Neighbor<>("a", "a", 2, 1.5);
        Neighbor<String, String> n2 = new Neighbor<>("a", "a", 2, 1.5);
        assertEquals(0, n1.compareTo(n2));
    }

    @Test
    public void testSortArray() {
        Neighbor<String, String> n1 = new Neighbor<>("c", "c", 2, 3.0);
        Neighbor<String, String> n2 = new Neighbor<>("a", "a", 0, 1.0);
        Neighbor<String, String> n3 = new Neighbor<>("b", "b", 1, 2.0);
        @SuppressWarnings("unchecked")
        Neighbor<String, String>[] arr = new Neighbor[]{n1, n2, n3};
        Arrays.sort(arr);
        assertEquals(0, arr[0].index());
        assertEquals(1, arr[1].index());
        assertEquals(2, arr[2].index());
    }

    @Test
    public void testToString() {
        Neighbor<String, String> n = new Neighbor<>("key", "val", 7, 0.5);
        String s = n.toString();
        assertTrue(s.contains("key"));
        assertTrue(s.contains("7"));
    }
}

