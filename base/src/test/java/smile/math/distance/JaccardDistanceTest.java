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
package smile.math.distance;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class JaccardDistanceTest {

    public JaccardDistanceTest() {
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    /**
     * Test of distance method on Sets, of class JaccardDistance.
     */
    @Test
    public void testDistanceSet() {
        System.out.println("distance set");
        Set<Integer> a = new HashSet<>();
        a.add(1);
        a.add(2);
        a.add(3);
        a.add(4);

        Set<Integer> b = new HashSet<>();
        b.add(3);
        b.add(4);
        b.add(5);
        b.add(6);

        // union = {1,2,3,4,5,6}, intersection = {3,4}, J = 2/6 = 1/3, dist = 1 - 1/3 = 2/3
        assertEquals(0.6666667, JaccardDistance.d(a, b), 1E-7);
    }

    /**
     * Test of distance method on arrays, of class JaccardDistance.
     */
    @Test
    public void testDistanceArray() {
        System.out.println("distance array");
        Integer[] a = {1, 2, 3, 4};
        Integer[] b = {3, 4, 5, 6};

        JaccardDistance<Integer> dist = new JaccardDistance<>();
        // union = {1,2,3,4,5,6}, intersection = {3,4} => J = 2/6; dist = 2/3
        assertEquals(0.6666667, dist.d(a, b), 1E-7);
    }

    @Test
    public void testDistanceIdentical() {
        System.out.println("distance identical");
        Integer[] a = {1, 2, 3};
        JaccardDistance<Integer> dist = new JaccardDistance<>();
        assertEquals(0.0, dist.d(a, a), 1E-9);
    }

    @Test
    public void testDistanceDisjoint() {
        System.out.println("distance disjoint");
        Integer[] a = {1, 2};
        Integer[] b = {3, 4};
        JaccardDistance<Integer> dist = new JaccardDistance<>();
        assertEquals(1.0, dist.d(a, b), 1E-9);
    }

    @Test
    public void testDistanceSubset() {
        System.out.println("distance subset");
        Integer[] a = {1, 2, 3};
        Integer[] b = {1, 2, 3, 4, 5};
        JaccardDistance<Integer> dist = new JaccardDistance<>();
        // union={1,2,3,4,5}, intersection={1,2,3}, J=3/5, dist=2/5
        assertEquals(0.4, dist.d(a, b), 1E-9);
    }

    @Test
    public void testDistanceSetIdentical() {
        System.out.println("distance set identical");
        Set<String> a = new HashSet<>();
        a.add("a");
        a.add("b");
        assertEquals(0.0, JaccardDistance.d(a, a), 1E-9);
    }
}